package org.uengine.hwlife.rule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncRequest;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncResponse;
import org.uengine.hwlife.rule.dto.RoleAssignRulesSyncResponseItem;
import org.uengine.hwlife.rule.dto.RuleCandidate;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;
import org.uengine.hwlife.rule.repository.BpmRoleAssignRuleRepository;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 규칙 기반 담당자 배정의 I/O 담당 서비스.
 *
 * <p>{@code RuleBasedRoleResolutionContext}(POJO)는 순수 알고리즘만 갖고, DB/외부 호출은 이 빈에 위임한다.
 * 기동 시 {@link RuleRoleResolutionSupport} 에 자기 자신을 등록해 POJO 에서 정적 접근 가능하게 한다.</p>
 *
 * <ul>
 *   <li>{@link #syncRoleAssignRules(String, String)} : 역량 기준정보 일 1회 동기화 후 BPM_ROLE_ASSIGN_RULE 조회</li>
 *   <li>{@link #queryWorkload(String, Collection)} : 후보 담당자별 진행중 업무량</li>
 * </ul>
 */
@Service
public class RuleRoleResolutionService {

    private static final Logger log = LoggerFactory.getLogger(RuleRoleResolutionService.class);

    private static final String USE_Y = "Y";
    private static final String USE_N = "N";
    /** WorklistEntity 의 완료 상태값(대문자 컨벤션). 이외는 '진행중'으로 집계. */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final BpmRoleAssignRuleRepository ruleRepository;
    private final EsbClient esbClient;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RuleRoleResolutionService(
            BpmRoleAssignRuleRepository ruleRepository,
            EsbClient esbClient,
            PlatformTransactionManager transactionManager) {
        this.ruleRepository = ruleRepository;
        this.esbClient = esbClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void register() {
        RuleRoleResolutionSupport.register(this);
    }

    /**
     * 역량 기준정보를 필요 시 외부(ESB)에서 동기화한 뒤 배정 규칙(BPM_ROLE_ASSIGN_RULE)을 반환한다.
     *
     * <p>동기화 단위는 {@code policyId}. {@code synced_at} 이 오늘이 아니면 ESB 조회 후 적재한다.
     * ESB 호출에 성공하면 값 변경 여부와 무관하게 {@code synced_at} 을 갱신하고,
     * 실패 시에는 {@code synced_at} 을 바꾸지 않고 로컬 규칙으로 배정을 이어간다.</p>
     */
    public List<RuleCandidate> syncRoleAssignRules(String policyId, String difficulty) {
        if (needsSyncToday(policyId)) {
            trySyncFromEsb(policyId);
        }

        List<BpmRoleAssignRule> rules = query(policyId, difficulty);
        List<RuleCandidate> result = new ArrayList<>();
        for (BpmRoleAssignRule r : rules) {
            double w = r.getWeight() != null ? r.getWeight() : 0d;
            result.add(new RuleCandidate(r.getEndpoint(), r.getDifficulty(), w));
        }
        return result;
    }

    /** 해당 정책의 최근 동기화 시각이 없거나 오늘이 아니면 true. */
    boolean needsSyncToday(String policyId) {
        if (policyId == null || policyId.trim().isEmpty()) {
            return false;
        }
        Date maxSyncedAt = ruleRepository.findMaxSyncedAtByPolicyId(policyId);
        if (maxSyncedAt == null) {
            return true;
        }
        LocalDate syncedDay = maxSyncedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return !LocalDate.now(ZoneId.systemDefault()).equals(syncedDay);
    }

    /**
     * ESB 조회 → 성공 시에만 DB 적재({@code synced_at} 갱신).
     * 실패·응답 null 이면 로컬 유지.
     */
    private void trySyncFromEsb(String policyId) {
        RoleAssignRulesSyncResponse remote;
        try {
            remote = fetchAssignRules(policyId);
        } catch (Exception e) {
            log.warn("[RuleRoleResolution] 역량 기준정보 ESB 조회 실패 — 로컬 규칙 유지 | policyId={} cause={}",
                    policyId, e.getMessage());
            return;
        }
        if (remote == null) {
            log.warn("[RuleRoleResolution] 역량 기준정보 ESB 응답 없음 — 로컬 규칙 유지 | policyId={}", policyId);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> persistSyncedRules(policyId, remote));
            log.info("[RuleRoleResolution] 역량 기준정보 동기화 완료 | policyId={} items={}",
                    policyId, remote.getCpabList() != null ? remote.getCpabList().size() : 0);
        } catch (Exception e) {
            log.warn("[RuleRoleResolution] 역량 기준정보 적재 실패 — synced_at 미갱신 | policyId={} cause={}",
                    policyId, e.getMessage());
        }
    }

    /** 정책(역량명) 기준 역량 기준정보를 ESB 에서 조회. {@code cpabNm} = policyId. */
    private RoleAssignRulesSyncResponse fetchAssignRules(String policyId) {
        RoleAssignRulesSyncRequest request = new RoleAssignRulesSyncRequest();
        request.setCpabNm(policyId);
        return esbClient.send("", "", request, RoleAssignRulesSyncResponse.class);
    }

    /**
     * policyId 단위로 기존 규칙을 비활성화한 뒤 ESB 응답을 적재한다.
     * 값이 동일해도 {@code synced_at} 은 현재 시각으로 갱신한다.
     */
    void persistSyncedRules(String policyId, RoleAssignRulesSyncResponse remote) {
        Date now = new Date();
        List<BpmRoleAssignRule> existing = ruleRepository.findByPolicyId(policyId);

        // (difficulty|endpoint) → 기존 행 (재사용·갱신)
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
            String difficulty = item.getCpabLvdfLvelNm();
            String key = ruleKey(difficulty, endpoint);
            seen.add(key);

            BpmRoleAssignRule row = byKey.get(key);
            if (row == null) {
                row = new BpmRoleAssignRule();
                row.setPolicyId(policyId);
                row.setDifficulty(difficulty);
                row.setEndpoint(endpoint);
            }
            row.setWeight(item.getCpabWghdCnt() != null ? item.getCpabWghdCnt().doubleValue() : 0d);
            row.setUseYn(normalizeUseYn(item.getUseYn()));
            row.setSyncedAt(now);
            ruleRepository.save(row);
        }

        // 응답에 없는 기존 행: 비활성 + synced_at 갱신 (오늘 동기화 완료 표시 유지)
        for (BpmRoleAssignRule r : existing) {
            String key = ruleKey(r.getDifficulty(), r.getEndpoint());
            if (!seen.contains(key)) {
                r.setUseYn(USE_N);
                r.setSyncedAt(now);
                ruleRepository.save(r);
            }
        }

        // 기존 행이 없고 응답도 비어 있으면, 오늘 재호출을 막기 위한 동기화 마커 1건 적재
        if (existing.isEmpty() && seen.isEmpty()) {
            BpmRoleAssignRule marker = new BpmRoleAssignRule();
            marker.setPolicyId(policyId);
            marker.setUseYn(USE_N);
            marker.setSyncedAt(now);
            marker.setWeight(0d);
            ruleRepository.save(marker);
        }
    }

    private static String ruleKey(String difficulty, String endpoint) {
        return (difficulty != null ? difficulty : "") + "|" + (endpoint != null ? endpoint : "");
    }

    private static String normalizeUseYn(String useYn) {
        if (useYn == null || useYn.trim().isEmpty()) {
            return USE_Y;
        }
        return USE_Y.equalsIgnoreCase(useYn.trim()) ? USE_Y : USE_N;
    }

    private List<BpmRoleAssignRule> query(String policyId, String difficulty) {
        if (difficulty != null && !difficulty.trim().isEmpty()) {
            return ruleRepository.findByPolicyIdAndDifficultyAndUseYn(policyId, difficulty, USE_Y);
        }
        return ruleRepository.findByPolicyIdAndUseYn(policyId, USE_Y);
    }

    /**
     * 후보 담당자별 '진행중' 워크리스트 건수 집계.
     * (REF_ID 연속성/이력 가중이 필요하면 이 지점에서 반영)
     *
     * @return endpoint -> 진행중 건수 (후보 전원에 대해 최소 0 보장)
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> queryWorkload(String refId, Collection<String> endpoints) {
        Map<String, Integer> result = new HashMap<>();
        if (endpoints == null || endpoints.isEmpty()) {
            return result;
        }

        List<Object[]> rows = entityManager.createQuery(
                        "select w.endpoint, count(w) from WorklistEntity w " +
                        "where w.endpoint in :endpoints and w.status <> :completed " +
                        "group by w.endpoint", Object[].class)
                .setParameter("endpoints", endpoints)
                .setParameter("completed", STATUS_COMPLETED)
                .getResultList();

        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).intValue());
        }
        for (String ep : endpoints) {
            result.putIfAbsent(ep, 0);
        }
        return result;
    }
}
