package org.uengine.hwlife.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.five.repository.RoleMappingRepository;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;
import org.uengine.hwlife.rule.repository.BpmRoleAssignRuleRepository;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 규칙 기반 담당자 배정의 I/O 담당 서비스.
 *
 * <p>{@code RuleBasedRoleResolutionContext}(POJO)는 순수 알고리즘만 갖고, DB 조회는 이 빈에 위임한다.
 * 기동 시 {@link RuleRoleResolutionSupport} 에 자기 자신을 등록해 POJO 에서 정적 접근 가능하게 한다.</p>
 *
 * <ul>
 *   <li>{@link #loadRules(String, String)} : BPM_ROLE_ASSIGN_RULE 조회</li>
 *   <li>{@link #queryWorkload(String, String, String, Collection)} : 후보 담당자별 진행중 업무량</li>
 * </ul>
 */
@Service
@EnableScheduling
public class RuleRoleResolutionService {

    private static final String USE_Y = "Y";
    /** WorklistEntity 의 완료 상태값(대문자 컨벤션). 이외는 '진행중'으로 집계. */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final BpmRoleAssignRuleRepository ruleRepository;
    private final ExternalRoleAssignRuleClient externalClient;
    private final RoleMappingRepository roleMappingRepository;
    private final AbsenceRepository absenceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RuleRoleResolutionService(BpmRoleAssignRuleRepository ruleRepository,
                                     ExternalRoleAssignRuleClient externalClient,
                                     RoleMappingRepository roleMappingRepository) {
        this(ruleRepository, externalClient, roleMappingRepository, null);
    }

    @Autowired
    public RuleRoleResolutionService(BpmRoleAssignRuleRepository ruleRepository,
                                     ExternalRoleAssignRuleClient externalClient,
                                     RoleMappingRepository roleMappingRepository,
                                     AbsenceRepository absenceRepository) {
        this.ruleRepository = ruleRepository;
        this.externalClient = externalClient;
        this.roleMappingRepository = roleMappingRepository;
        this.absenceRepository = absenceRepository;
    }

    @PostConstruct
    public void register() {
        RuleRoleResolutionSupport.register(this);
    }

    /**
     * 정책/난이도 기준 배정 규칙(BPM_ROLE_ASSIGN_RULE) 조회.
     *
     * <p>당일 동기화된 규칙은 로컬에서 사용하고, 누락되었거나 오늘 기준으로 오래된 경우에만
     * 외부 기준정보를 직접 조회한다.</p>
     */
    @Transactional
    public List<RuleCandidate> loadRules(String policyId, String difficulty) {
        List<BpmRoleAssignRule> rules = query(policyId, difficulty);
        if (needsRefresh(rules)) {
            syncRulesFromExternal(policyId, difficulty);
            rules = query(policyId, difficulty);
        }

        List<RuleCandidate> result = new ArrayList<>();
        for (BpmRoleAssignRule r : rules) {
            double w = r.getWeight() != null ? r.getWeight() : 0d;
            result.add(new RuleCandidate(r.getEndpoint(), r.getDifficulty(), w));
        }
        return result;
    }

    @Transactional
    public List<BpmRoleAssignRule> listRulesForDisplay(String policyId, String difficulty) {
        String normalizedPolicyId = trim(policyId);
        String normalizedDifficulty = trim(difficulty);

        if (!isNotEmpty(normalizedPolicyId)) {
            return new ArrayList<>();
        }

        if (isNotEmpty(normalizedDifficulty)) {
            List<BpmRoleAssignRule> rules = query(normalizedPolicyId, normalizedDifficulty);
            if (needsRefresh(rules)) {
                syncRulesFromExternal(normalizedPolicyId, normalizedDifficulty);
            }
        }

        if (isNotEmpty(normalizedDifficulty)) {
            return ruleRepository.findByPolicyIdAndDifficultyOrderByEndpointAsc(normalizedPolicyId, normalizedDifficulty);
        }
        return ruleRepository.findByPolicyIdOrderByDifficultyAscEndpointAsc(normalizedPolicyId);
    }

    @Transactional(readOnly = true)
    public String resolveActiveDelegateEndpoint(String endpoint) {
        String normalizedEndpoint = trim(endpoint);
        if (!isNotEmpty(normalizedEndpoint) || absenceRepository == null) {
            return null;
        }

        List<AbsenceEntity> activeAbsences = absenceRepository.findActiveAt(normalizedEndpoint, new Date());
        if (activeAbsences == null || activeAbsences.isEmpty()) {
            return null;
        }

        return trim(activeAbsences.get(0).getAgentUserId());
    }

    /**
     * 이미 적재된 정책/난이도 조합을 하루에 한 번 원천 기준정보로 갱신한다.
     * 새 정책은 첫 배정 시 {@link #loadRules(String, String)} 의 누락 fallback으로 적재된다.
     */
    @Scheduled(cron = "${uengine.role-assign-rule.refresh-cron:0 0 0 * * *}")
    @Transactional
    public void refreshRulesDaily() {
        Set<String> ruleKeys = new HashSet<>();
        for (BpmRoleAssignRule rule : ruleRepository.findAll()) {
            String policyId = trim(rule.getPolicyId());
            String difficulty = trim(rule.getDifficulty());
            if (isNotEmpty(policyId) && isNotEmpty(difficulty)) {
                ruleKeys.add(policyId + "\u0000" + difficulty);
            }
        }

        for (String ruleKey : ruleKeys) {
            int separatorIndex = ruleKey.indexOf('\u0000');
            String policyId = ruleKey.substring(0, separatorIndex);
            String difficulty = ruleKey.substring(separatorIndex + 1);
            try {
                syncRulesFromExternal(policyId, difficulty);
            } catch (RuntimeException ignored) {
                // 한 정책 갱신 실패가 다른 정책의 일일 갱신을 중단시키지 않도록 한다.
            }
        }
    }

    private void syncRulesFromExternal(String policyId, String difficulty) {
        List<ExternalRoleAssignRule> externalRules = externalClient.fetchRules(policyId, difficulty);
        if (externalRules == null || externalRules.isEmpty()) {
            return;
        }

        Date syncedAt = new Date();
        List<BpmRoleAssignRule> entities = new ArrayList<>();
        for (ExternalRoleAssignRule external : externalRules) {
            if (external == null) {
                continue;
            }

            String endpoint = trim(firstNonBlank(external.getEndpoint(), external.getEmployeeNo()));
            if (!isNotEmpty(endpoint)) {
                continue;
            }

            String ruleDifficulty = trim(isNotEmpty(external.getDifficulty()) ? external.getDifficulty() : difficulty);
            Optional<BpmRoleAssignRule> existing =
                    ruleRepository.findFirstByPolicyIdAndDifficultyAndEndpoint(policyId, ruleDifficulty, endpoint);

            BpmRoleAssignRule entity = existing.orElseGet(BpmRoleAssignRule::new);
            entity.setPolicyId(policyId);
            entity.setDifficulty(ruleDifficulty);
            entity.setEndpoint(endpoint);
            entity.setWeight(normalizeWeight(external.getWeight()));
            entity.setUseYn(normalizeUseYn(external.getUseYn()));
            entity.setSyncedAt(syncedAt);
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            ruleRepository.saveAll(entities);
            ruleRepository.flush();
        }
    }

    private boolean needsRefresh(List<BpmRoleAssignRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        LocalDate today = LocalDate.now();
        return rules.stream().anyMatch(rule -> {
            Date syncedAt = rule.getSyncedAt();
            return syncedAt == null || syncedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(today);
        });
    }

    private List<BpmRoleAssignRule> query(String policyId, String difficulty) {
        if (difficulty != null && !difficulty.trim().isEmpty()) {
            return ruleRepository.findByPolicyIdAndDifficultyAndUseYn(policyId, difficulty, USE_Y);
        }
        return ruleRepository.findByPolicyIdAndUseYn(policyId, USE_Y);
    }

    private static Double normalizeWeight(Double weight) {
        if (weight == null || weight < 1d) {
            return 1d;
        }
        return weight;
    }

    private static String firstNonBlank(String first, String second) {
        return isNotEmpty(first) ? first : second;
    }

    private static String normalizeUseYn(String useYn) {
        return isNotEmpty(useYn) ? useYn.trim().toUpperCase() : USE_Y;
    }

    private static String trim(String value) {
        return value != null ? value.trim() : null;
    }

    private static boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 후보 담당자별 '진행중' 워크리스트 건수 집계.
     * (REF_ID 연속성/이력 가중이 필요하면 이 지점에서 반영)
     *
     * @return endpoint -> 진행중 건수 (후보 전원에 대해 최소 0 보장)
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> queryWorkload(String policyId, String difficulty, String refId, Collection<String> endpoints) {
        Map<String, Integer> result = new HashMap<>();
        if (endpoints == null || endpoints.isEmpty()) {
            return result;
        }

        StringBuilder jpql = new StringBuilder(
                "select w.endpoint, count(distinct w.taskId) " +
                "from WorklistEntity w, RoleMappingEntity rm " +
                "where rm.processInstance.instId = w.instId " +
                "and rm.roleName = w.roleName " +
                "and rm.endpoint = w.endpoint " +
                "and w.endpoint in :endpoints " +
                "and w.status <> :completed");
        if (isNotEmpty(policyId)) {
            jpql.append(" and rm.policyId = :policyId");
        }
        if (isNotEmpty(difficulty)) {
            jpql.append(" and rm.difficulty = :difficulty");
        }
        jpql.append(" group by w.endpoint");

        var query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("endpoints", endpoints)
                .setParameter("completed", STATUS_COMPLETED);
        if (isNotEmpty(policyId)) {
            query.setParameter("policyId", policyId.trim());
        }
        if (isNotEmpty(difficulty)) {
            query.setParameter("difficulty", difficulty.trim());
        }

        for (Object[] row : query.getResultList()) {
            result.put((String) row[0], ((Number) row[1]).intValue());
        }
        for (String ep : endpoints) {
            result.putIfAbsent(ep, 0);
        }
        return result;
    }

    @Transactional
    public void recordRoleAssignment(ProcessInstance instance, String roleName, RoleMapping mapping) {
        if (!(instance instanceof JPAProcessInstance) || mapping == null) {
            return;
        }

        String policyId = trim(mapping.getExtendedProperty("POLICY_ID"));
        String difficulty = trim(mapping.getExtendedProperty("DIFFICULTY"));
        if (!isNotEmpty(policyId) || !isNotEmpty(difficulty)) {
            return;
        }

        ProcessInstanceEntity processInstance = ((JPAProcessInstance) instance).getProcessInstanceEntity();
        if (processInstance == null || processInstance.getInstId() == null) {
            return;
        }

        String endpoint = trim(mapping.getEndpoint());
        if (!isNotEmpty(roleName) || !isNotEmpty(endpoint)) {
            return;
        }

        RoleMappingEntity entity = roleMappingRepository
                .findLatestRuleAssignment(processInstance.getInstId(), roleName, endpoint, policyId, difficulty)
                .orElseGet(RoleMappingEntity::new);
        entity.setProcessInstance(processInstance);
        entity.setRoleName(roleName);
        entity.setEndpoint(endpoint);
        entity.setResName(mapping.getResourceName());
        entity.setGroupId(mapping.getGroupId());
        entity.setAssignType(mapping.getAssignType());
        entity.setAssignParam1(mapping.getAssignParam1());
        entity.setDispatchOption(mapping.getDispatchingOption());
        entity.setDispatchParam1(mapping.getDispatchParam1());
        entity.setValue(mapping.toString());
        entity.setPolicyId(policyId);
        entity.setDifficulty(difficulty);
        entity.setRefId(trim(mapping.getExtendedProperty("REF_ID")));
        entity.setAssignedAt(new Date());
        roleMappingRepository.save(entity);
    }
}
