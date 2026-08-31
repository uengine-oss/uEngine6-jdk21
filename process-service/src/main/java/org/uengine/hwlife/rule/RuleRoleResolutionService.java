package org.uengine.hwlife.rule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.five.repository.RoleMappingRepository;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncRequest;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncResponse;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncResponseItem;
import org.uengine.hwlife.rule.dto.RuleCandidate;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;
import org.uengine.hwlife.rule.repository.BpmRoleAssignRuleRepository;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;
import org.uengine.webservices.worklist.DefaultWorkList;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 규칙 기반 담당자 배정의 I/O 담당 서비스.
 *
 * <p>{@code RuleBasedRoleResolutionContext}(POJO)는 순수 알고리즘만 갖고, DB/외부 호출은 이 빈에 위임한다.</p>
 */
@Service
public class RuleRoleResolutionService {

    private static final Logger log = LoggerFactory.getLogger(RuleRoleResolutionService.class);

    /** 활성 Y=true, 비활성 N=false. */
    private static final Boolean ACTIVE_USE = Boolean.TRUE;
    private static final Boolean INACTIVE_USE = Boolean.FALSE;

    /** 업무량 집계: {@link DefaultWorkList#WORKITEM_STATUS_NEW} + {@link DefaultWorkList#WORKITEM_STATUS_COMPLETED}. */
    private static final List<String> WORKLOAD_STATUSES = List.of(
            DefaultWorkList.WORKITEM_STATUS_NEW,
            DefaultWorkList.WORKITEM_STATUS_COMPLETED);

    private final BpmRoleAssignRuleRepository ruleRepository;
    private final RoleMappingRepository roleMappingRepository;
    private final AbsenceRepository absenceRepository;
    private final EsbClient esbClient;
    private final TransactionTemplate transactionTemplate;
    private final RuleRoleResolutionService self;

    @PersistenceContext
    private EntityManager entityManager;

    public RuleRoleResolutionService(
            BpmRoleAssignRuleRepository ruleRepository,
            RoleMappingRepository roleMappingRepository,
            AbsenceRepository absenceRepository,
            EsbClient esbClient,
            PlatformTransactionManager transactionManager,
            @Lazy @Autowired RuleRoleResolutionService self) {
        this.ruleRepository = ruleRepository;
        this.roleMappingRepository = roleMappingRepository;
        this.absenceRepository = absenceRepository;
        this.esbClient = esbClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.self = self;
    }

    @PostConstruct
    public void register() {
        RuleRoleResolutionSupport.register(self);
    }

    /**
     * 역량 기준정보 조회. {@code synced_at} 이 오늘이면 DB를 사용하고, 아니면 ESB 동기화 후 조회한다.
     */
    public List<RuleCandidate> syncRoleAssignRules(String policyId, String difficulty) {
        if (!syncedToday(policyId, difficulty)) {
            trySyncFromEsb(policyId, difficulty);
        }
        List<RuleCandidate> rules = toCandidates(query(policyId, difficulty));
        if (rules.isEmpty()) {
            log.warn("[RuleRoleResolution] 배정 규칙 없음 | policyId={} difficulty={} syncedToday={}",
                    policyId, difficulty, syncedToday(policyId, difficulty));
        }
        return rules;
    }

    /**
     * 동일 {@code refId}(loanPcesMgmtNo) 로 이전 배정된 처리자 — 재배정 제외 대상.
     */
    @Transactional(readOnly = true)
    public Set<String> findExcludedEndpointsByRefId(String refId) {
        if (!isNotEmpty(refId)) {
            return Collections.emptySet();
        }
        return new HashSet<>(roleMappingRepository.findDistinctEndpointsByRefId(refId));
    }

    /**
     * 부재 중이면 대결자(agentUserId)를 반환하고, 아니면 원 처리자를 그대로 반환한다.
     */
    @Transactional(readOnly = true)
    public String resolveAbsentAssignee(String endpoint) {
        if (!isNotEmpty(endpoint)) {
            return endpoint;
        }
        List<AbsenceEntity> active = absenceRepository.findActiveByUserIdAt(endpoint, new Date());
        if (active.isEmpty()) {
            return endpoint;
        }
        String agent = active.get(0).getAgentUserId();
        if (isNotEmpty(agent)) {
            log.debug("[RuleRoleResolution] 부재 대리 배정 | absent={} agent={}", endpoint, agent);
            return agent;
        }
        return endpoint;
    }

    /**
     * 배정 결과를 {@code BPM_ROLEMAPPING} 에 적재 — refId 이력 조회·재배정 제외에 사용.
     */
    public void persistRoleMapping(ProcessInstance instance, RoleMapping mapping,
            String policyId, String difficulty, String refId) {
        if (instance == null) {
            log.warn("[RuleRoleResolution] BPM_ROLEMAPPING 적재 생략 — instance null | policyId={} refId={}",
                    policyId, refId);
            return;
        }
        if (mapping == null || !isNotEmpty(mapping.getEndpoint())) {
            log.warn("[RuleRoleResolution] BPM_ROLEMAPPING 적재 생략 — endpoint 없음 | instId={} policyId={} refId={}",
                    instance.getInstanceId(), policyId, refId);
            return;
        }

        ProcessInstanceEntity processInstance = resolveProcessInstance(instance);
        if (processInstance == null || processInstance.getInstId() == null) {
            log.warn("[RuleRoleResolution] BPM_ROLEMAPPING 적재 실패 — ProcessInstanceEntity 없음 | instId={} policyId={} endpoint={} refId={}",
                    instance.getInstanceId(), policyId, mapping.getEndpoint(), refId);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                RoleMappingEntity entity = new RoleMappingEntity();
                entity.setProcessInstance(processInstance);
                entity.setRootProcessInstance(resolveRootProcessInstance(processInstance));
                entity.setRoleName(mapping.getName());
                entity.setEndpoint(mapping.getEndpoint());
                entity.setResName(mapping.getResourceName());
                entity.setAssignType(mapping.getAssignType());
                entity.setDispatchOption(mapping.getDispatchingOption());
                entity.setPolicyId(policyId);
                entity.setDifficulty(difficulty);
                entity.setRefId(refId);
                RoleMappingEntity saved = roleMappingRepository.save(entity);
                entityManager.flush();
                log.info("[RuleRoleResolution] BPM_ROLEMAPPING 적재 완료 | roleMappingId={} instId={} endpoint={} policyId={} difficulty={} refId={}",
                        saved.getRoleMappingId(), processInstance.getInstId(), saved.getEndpoint(),
                        policyId, difficulty, refId);
            });
        } catch (Exception e) {
            log.error("[RuleRoleResolution] BPM_ROLEMAPPING 적재 실패 | instId={} endpoint={} policyId={} difficulty={} refId={}",
                    instance.getInstanceId(), mapping.getEndpoint(), policyId, difficulty, refId, e);
        }
    }

    private ProcessInstanceEntity resolveProcessInstance(ProcessInstance instance) {
        if (instance instanceof JPAProcessInstance jpaInstance) {
            ProcessInstanceEntity entity = jpaInstance.getProcessInstanceEntity();
            if (entity != null && entity.getInstId() != null) {
                return entity;
            }
            log.warn("[RuleRoleResolution] JPAProcessInstance 에 ProcessInstanceEntity 없음 | instId={}",
                    instance.getInstanceId());
        }
        return findProcessInstanceEntity(instance);
    }

    private ProcessInstanceEntity findProcessInstanceEntity(ProcessInstance instance) {
        try {
            return entityManager.find(ProcessInstanceEntity.class, Long.valueOf(instance.getInstanceId()));
        } catch (Exception e) {
            log.warn("[RuleRoleResolution] ProcessInstanceEntity 조회 실패 | instId={}",
                    instance.getInstanceId(), e);
            return null;
        }
    }

    private ProcessInstanceEntity resolveRootProcessInstance(ProcessInstanceEntity processInstance) {
        Long rootInstId = processInstance.getRootInstId();
        if (rootInstId == null) {
            return processInstance;
        }
        ProcessInstanceEntity root = entityManager.find(ProcessInstanceEntity.class, rootInstId);
        return root != null ? root : processInstance;
    }

    boolean syncedToday(String policyId, String difficulty) {
        Date maxSyncedAt = isNotEmpty(difficulty)
                ? ruleRepository.findMaxSyncedAtByPolicyIdAndDifficulty(policyId, difficulty)
                : ruleRepository.findMaxSyncedAtByPolicyId(policyId);
        if (maxSyncedAt == null) {
            return false;
        }
        LocalDate syncedDay = maxSyncedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return LocalDate.now(ZoneId.systemDefault()).equals(syncedDay);
    }

    private void trySyncFromEsb(String policyId, String difficulty) {
        RoleAssignRulesSyncResponse remote;
        try {
            remote = fetchAssignRules(policyId);
        } catch (Exception e) {
            log.warn("[RuleRoleResolution] 역량 기준정보 ESB 조회 실패 — 로컬 규칙 유지 | policyId={} difficulty={} cause={}",
                    policyId, difficulty, e.getMessage(), e);
            return;
        }
        if (remote == null) {
            log.warn("[RuleRoleResolution] 역량 기준정보 ESB 응답 없음 — 로컬 규칙 유지 | policyId={} difficulty={}",
                    policyId, difficulty);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                persistSyncedRules(policyId, remote);
                markDifficultySyncedIfEmpty(policyId, difficulty);
            });
            log.info("[RuleRoleResolution] 역량 기준정보 동기화 완료 | policyId={} difficulty={} items={}",
                    policyId, difficulty, remote.getCpabList() != null ? remote.getCpabList().size() : 0);
        } catch (Exception e) {
            log.warn("[RuleRoleResolution] 역량 기준정보 적재 실패 | policyId={} difficulty={} cause={}",
                    policyId, difficulty, e.getMessage(), e);
        }
    }

    private RoleAssignRulesSyncResponse fetchAssignRules(String policyId) {
        RoleAssignRulesSyncRequest request = new RoleAssignRulesSyncRequest();
        request.setCpabNm(policyId);
        return esbClient.send("", "", request, RoleAssignRulesSyncResponse.class);
    }

    void persistSyncedRules(String policyId, RoleAssignRulesSyncResponse remote) {
        Date now = new Date();
        List<BpmRoleAssignRule> existing = ruleRepository.findByPolicyId(policyId);

        Map<String, BpmRoleAssignRule> byKey = new HashMap<>();
        for (BpmRoleAssignRule r : existing) {
            byKey.put(ruleKey(r.getDifficulty(), r.getEndpoint()), r);
        }

        Set<String> seen = new HashSet<>();
        List<RoleAssignRulesSyncResponseItem> items =
                remote.getCpabList() != null ? remote.getCpabList() : List.of();

        for (RoleAssignRulesSyncResponseItem item : items) {
            if (item == null) {
                continue;
            }
            String endpoint = item.getHndrEmnb();
            if (endpoint == null || endpoint.trim().isEmpty()) {
                continue;
            }
            String itemDifficulty = item.getCpabLvdfLvelNm();
            String key = ruleKey(itemDifficulty, endpoint);
            seen.add(key);

            BpmRoleAssignRule row = byKey.get(key);
            if (row == null) {
                row = new BpmRoleAssignRule();
                row.setPolicyId(policyId);
                row.setDifficulty(itemDifficulty);
                row.setEndpoint(endpoint);
            }
            row.setWeight(item.getCpabWghdCnt() != null ? item.getCpabWghdCnt() : 0);
            row.setUseYn(toStoredUseYn(item.getUseYn()));
            row.setSyncedAt(now);
            ruleRepository.save(row);
        }

        for (BpmRoleAssignRule r : existing) {
            if (r.getEndpoint() == null || r.getEndpoint().trim().isEmpty()) {
                continue;
            }
            String key = ruleKey(r.getDifficulty(), r.getEndpoint());
            if (!seen.contains(key)) {
                r.setUseYn(INACTIVE_USE);
                r.setSyncedAt(now);
                ruleRepository.save(r);
            }
        }
    }

    void markDifficultySyncedIfEmpty(String policyId, String difficulty) {
        if (!query(policyId, difficulty).isEmpty()) {
            return;
        }
        if (syncedToday(policyId, difficulty)) {
            return;
        }
        BpmRoleAssignRule marker = new BpmRoleAssignRule();
        marker.setPolicyId(policyId);
        if (isNotEmpty(difficulty)) {
            marker.setDifficulty(difficulty);
        }
        marker.setUseYn(INACTIVE_USE);
        marker.setSyncedAt(new Date());
        marker.setWeight(0);
        ruleRepository.save(marker);
    }

    /**
     * 후보 담당자별 업무량 집계 — <b>전월 1일 00:00 ~ 오늘</b>, {@code startDate} 기준.
     * <p>{@link DefaultWorkList#WORKITEM_STATUS_NEW}(진행중) + {@link DefaultWorkList#WORKITEM_STATUS_COMPLETED}(완료) 만 합산.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> queryWorkload(String refId, Collection<String> endpoints) {
        Map<String, Integer> result = new HashMap<>();
        if (endpoints == null || endpoints.isEmpty()) {
            return result;
        }

        Date periodStart = workloadPeriodStart();
        Date periodEndExclusive = workloadPeriodEndExclusive();

        List<Object[]> rows = entityManager.createQuery(
                        "select w.endpoint, count(w) from WorklistEntity w "
                                + "where w.endpoint in :endpoints "
                                + "and w.startDate >= :periodStart and w.startDate < :periodEndExclusive "
                                + "and upper(w.status) in :workloadStatuses "
                                + "group by w.endpoint", Object[].class)
                .setParameter("endpoints", endpoints)
                .setParameter("periodStart", periodStart)
                .setParameter("periodEndExclusive", periodEndExclusive)
                .setParameter("workloadStatuses", WORKLOAD_STATUSES)
                .getResultList();

        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).intValue());
        }
        for (String ep : endpoints) {
            result.putIfAbsent(ep, 0);
        }
        return result;
    }

    /** 전월 1일 00:00 (시스템 타임존). */
    private Date workloadPeriodStart() {
        LocalDate firstOfPreviousMonth = LocalDate.now(ZoneId.systemDefault()).minusMonths(1).withDayOfMonth(1);
        return Date.from(firstOfPreviousMonth.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 내일 00:00 — 오늘 23:59:59 까지 포함하기 위한 exclusive 상한. */
    private Date workloadPeriodEndExclusive() {
        LocalDate tomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1);
        return Date.from(tomorrow.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private List<BpmRoleAssignRule> query(String policyId, String difficulty) {
        if (isNotEmpty(difficulty)) {
            return ruleRepository.findByPolicyIdAndDifficultyAndUseYn(policyId, difficulty, ACTIVE_USE);
        }
        return ruleRepository.findByPolicyIdAndUseYn(policyId, ACTIVE_USE);
    }

    private List<RuleCandidate> toCandidates(List<BpmRoleAssignRule> rules) {
        List<RuleCandidate> result = new ArrayList<>();
        for (BpmRoleAssignRule r : rules) {
            if (r.getEndpoint() == null || r.getEndpoint().trim().isEmpty()) {
                continue;
            }
            int w = r.getWeight() != null ? r.getWeight() : 0;
            result.add(new RuleCandidate(r.getEndpoint(), r.getDifficulty(), w));
        }
        return result;
    }

    private static String ruleKey(String difficulty, String endpoint) {
        return (difficulty != null ? difficulty : "") + "|" + (endpoint != null ? endpoint : "");
    }

    /** ESB Y/N → DB boolean (Y=true, N=false). 미지정 시 true(활성). */
    private static Boolean toStoredUseYn(String esbUseYn) {
        if (esbUseYn == null || esbUseYn.trim().isEmpty()) {
            return ACTIVE_USE;
        }
        return "Y".equalsIgnoreCase(esbUseYn.trim());
    }

    private static boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
