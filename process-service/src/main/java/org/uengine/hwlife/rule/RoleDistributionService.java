package org.uengine.hwlife.rule;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class RoleDistributionService {

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final RuleRoleResolutionService ruleService;

    @PersistenceContext
    private EntityManager entityManager;

    public RoleDistributionService(RuleRoleResolutionService ruleService) {
        this.ruleService = ruleService;
    }

    @Transactional(readOnly = true)
    public RoleDistributionSummary summarize(String policyId, String difficulty, String processDefinitionId) {
        List<BpmRoleAssignRule> rules = ruleService.listRulesForDisplay(policyId, difficulty);
        List<String> endpoints = rules.stream()
                .filter(rule -> "Y".equalsIgnoreCase(rule.getUseYn()))
                .map(BpmRoleAssignRule::getEndpoint)
                .filter(endpoint -> endpoint != null && !endpoint.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        RoleDistributionSummary summary = new RoleDistributionSummary();
        summary.policyId = policyId;
        summary.difficulty = difficulty;
        summary.processDefinitionId = processDefinitionId;
        summary.rules = rules.stream().map(RuleRow::from).collect(Collectors.toList());

        if (endpoints.isEmpty()) {
            summary.users = new ArrayList<>();
            summary.processes = new ArrayList<>();
            summary.recentWorkItems = new ArrayList<>();
            summary.totals = new SummaryTotals();
            return summary;
        }

        Map<String, Long> activeCounts = countByEndpoint(endpoints, policyId, difficulty, processDefinitionId, true);
        Map<String, Long> totalCounts = countByEndpoint(endpoints, policyId, difficulty, processDefinitionId, false);
        long activeTotal = activeCounts.values().stream().mapToLong(Long::longValue).sum();
        double weightTotal = rules.stream()
                .filter(rule -> "Y".equalsIgnoreCase(rule.getUseYn()))
                .mapToDouble(rule -> rule.getWeight() != null ? rule.getWeight() : 0d)
                .sum();

        summary.users = rules.stream()
                .filter(rule -> "Y".equalsIgnoreCase(rule.getUseYn()))
                .map(rule -> {
                    UserDistributionRow row = new UserDistributionRow();
                    row.endpoint = rule.getEndpoint();
                    row.difficulty = rule.getDifficulty();
                    row.weight = rule.getWeight() != null ? rule.getWeight() : 0d;
                    row.targetRatio = weightTotal > 0 ? row.weight / weightTotal : 0d;
                    row.activeCount = activeCounts.getOrDefault(row.endpoint, 0L);
                    row.totalCount = totalCounts.getOrDefault(row.endpoint, 0L);
                    row.actualRatio = activeTotal > 0 ? (double) row.activeCount / activeTotal : 0d;
                    row.gap = row.actualRatio - row.targetRatio;
                    return row;
                })
                .collect(Collectors.toList());

        summary.processes = processRows(endpoints, policyId, difficulty, processDefinitionId);
        summary.recentWorkItems = recentRows(endpoints, policyId, difficulty, processDefinitionId);

        SummaryTotals totals = new SummaryTotals();
        totals.ruleCount = summary.rules.size();
        totals.userCount = summary.users.size();
        totals.activeWorkItemCount = activeTotal;
        totals.totalWorkItemCount = totalCounts.values().stream().mapToLong(Long::longValue).sum();
        totals.processCount = summary.processes.stream()
                .map(row -> row.defId)
                .filter(defId -> defId != null && !defId.trim().isEmpty())
                .distinct()
                .count();
        summary.totals = totals;

        return summary;
    }

    private Map<String, Long> countByEndpoint(List<String> endpoints, String policyId, String difficulty, String defId, boolean activeOnly) {
        StringBuilder jpql = new StringBuilder(
                "select w.endpoint, count(distinct w.taskId) from WorklistEntity w, RoleMappingEntity rm " +
                "where rm.processInstance.instId = w.instId " +
                "and rm.roleName = w.roleName " +
                "and rm.endpoint = w.endpoint " +
                "and w.endpoint in :endpoints");
        appendRuleMetadataFilter(jpql, policyId, difficulty);
        if (activeOnly) {
            jpql.append(" and (w.status is null or w.status <> :completed)");
        }
        if (isNotEmpty(defId)) {
            jpql.append(" and w.defId = :defId");
        }
        jpql.append(" group by w.endpoint");

        var query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("endpoints", endpoints);
        setRuleMetadataParameters(query, policyId, difficulty);
        if (activeOnly) {
            query.setParameter("completed", STATUS_COMPLETED);
        }
        if (isNotEmpty(defId)) {
            query.setParameter("defId", defId.trim());
        }

        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        for (String endpoint : endpoints) {
            result.putIfAbsent(endpoint, 0L);
        }
        return result;
    }

    private List<ProcessDistributionRow> processRows(List<String> endpoints, String policyId, String difficulty, String defId) {
        StringBuilder jpql = new StringBuilder(
                "select w.defId, w.defName, w.endpoint, count(distinct w.taskId) " +
                "from WorklistEntity w, RoleMappingEntity rm " +
                "where rm.processInstance.instId = w.instId " +
                "and rm.roleName = w.roleName " +
                "and rm.endpoint = w.endpoint " +
                "and w.endpoint in :endpoints and (w.status is null or w.status <> :completed)");
        appendRuleMetadataFilter(jpql, policyId, difficulty);
        if (isNotEmpty(defId)) {
            jpql.append(" and w.defId = :defId");
        }
        jpql.append(" group by w.defId, w.defName, w.endpoint order by w.defId asc, w.endpoint asc");

        var query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("endpoints", endpoints)
                .setParameter("completed", STATUS_COMPLETED);
        setRuleMetadataParameters(query, policyId, difficulty);
        if (isNotEmpty(defId)) {
            query.setParameter("defId", defId.trim());
        }

        List<ProcessDistributionRow> rows = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            ProcessDistributionRow dto = new ProcessDistributionRow();
            dto.defId = (String) row[0];
            dto.defName = (String) row[1];
            dto.endpoint = (String) row[2];
            dto.activeCount = ((Number) row[3]).longValue();
            rows.add(dto);
        }
        return rows;
    }

    private List<RecentWorkItemRow> recentRows(List<String> endpoints, String policyId, String difficulty, String defId) {
        StringBuilder jpql = new StringBuilder(
                "select distinct w from WorklistEntity w, RoleMappingEntity rm " +
                "where rm.processInstance.instId = w.instId " +
                "and rm.roleName = w.roleName " +
                "and rm.endpoint = w.endpoint " +
                "and w.endpoint in :endpoints");
        appendRuleMetadataFilter(jpql, policyId, difficulty);
        if (isNotEmpty(defId)) {
            jpql.append(" and w.defId = :defId");
        }
        jpql.append(" order by w.taskId desc");

        var query = entityManager.createQuery(jpql.toString(), WorklistEntity.class)
                .setParameter("endpoints", endpoints)
                .setMaxResults(30);
        setRuleMetadataParameters(query, policyId, difficulty);
        if (isNotEmpty(defId)) {
            query.setParameter("defId", defId.trim());
        }

        return query.getResultList().stream().map(RecentWorkItemRow::from).collect(Collectors.toList());
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void appendRuleMetadataFilter(StringBuilder jpql, String policyId, String difficulty) {
        if (isNotEmpty(policyId)) {
            jpql.append(" and rm.policyId = :policyId");
        }
        if (isNotEmpty(difficulty)) {
            jpql.append(" and rm.difficulty = :difficulty");
        }
    }

    private static <T> void setRuleMetadataParameters(jakarta.persistence.TypedQuery<T> query, String policyId, String difficulty) {
        if (isNotEmpty(policyId)) {
            query.setParameter("policyId", policyId.trim());
        }
        if (isNotEmpty(difficulty)) {
            query.setParameter("difficulty", difficulty.trim());
        }
    }

    public static class RoleDistributionSummary {
        public String policyId;
        public String difficulty;
        public String processDefinitionId;
        public SummaryTotals totals;
        public List<RuleRow> rules;
        public List<UserDistributionRow> users;
        public List<ProcessDistributionRow> processes;
        public List<RecentWorkItemRow> recentWorkItems;
    }

    public static class SummaryTotals {
        public long ruleCount;
        public long userCount;
        public long activeWorkItemCount;
        public long totalWorkItemCount;
        public long processCount;
    }

    public static class RuleRow {
        public Long ruleId;
        public String policyId;
        public String difficulty;
        public String endpoint;
        public Double weight;
        public String useYn;
        public Date syncedAt;

        public static RuleRow from(BpmRoleAssignRule rule) {
            RuleRow row = new RuleRow();
            row.ruleId = rule.getRuleId();
            row.policyId = rule.getPolicyId();
            row.difficulty = rule.getDifficulty();
            row.endpoint = rule.getEndpoint();
            row.weight = rule.getWeight();
            row.useYn = rule.getUseYn();
            row.syncedAt = rule.getSyncedAt();
            return row;
        }
    }

    public static class UserDistributionRow {
        public String endpoint;
        public String difficulty;
        public double weight;
        public double targetRatio;
        public long activeCount;
        public long totalCount;
        public double actualRatio;
        public double gap;
    }

    public static class ProcessDistributionRow {
        public String defId;
        public String defName;
        public String endpoint;
        public long activeCount;
    }

    public static class RecentWorkItemRow {
        public Long taskId;
        public Long instId;
        public String defId;
        public String defName;
        public String title;
        public String endpoint;
        public String status;
        public Date startDate;

        public static RecentWorkItemRow from(WorklistEntity workItem) {
            RecentWorkItemRow row = new RecentWorkItemRow();
            row.taskId = workItem.getTaskId();
            row.instId = workItem.getInstId();
            row.defId = workItem.getDefId();
            row.defName = workItem.getDefName();
            row.title = workItem.getTitle();
            row.endpoint = workItem.getEndpoint();
            row.status = workItem.getStatus();
            row.startDate = workItem.getStartDate();
            return row;
        }
    }
}
