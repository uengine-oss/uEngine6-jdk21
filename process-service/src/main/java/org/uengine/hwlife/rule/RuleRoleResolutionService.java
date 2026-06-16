package org.uengine.hwlife.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;
import org.uengine.hwlife.rule.repository.BpmRoleAssignRuleRepository;

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
 *   <li>{@link #queryWorkload(String, Collection)} : 후보 담당자별 진행중 업무량</li>
 * </ul>
 */
@Service
public class RuleRoleResolutionService {

    private static final String USE_Y = "Y";
    /** WorklistEntity 의 완료 상태값(대문자 컨벤션). 이외는 '진행중'으로 집계. */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final BpmRoleAssignRuleRepository ruleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RuleRoleResolutionService(BpmRoleAssignRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    public void register() {
        RuleRoleResolutionSupport.register(this);
    }

    /**
     * 정책/난이도 기준 배정 규칙(BPM_ROLE_ASSIGN_RULE) 조회.
     *
     * <p>확장 포인트: 규칙이 비어 있을 때 외부 기준정보에서 적재하는 등의 동기화가 필요하면
     * 이 메서드에서 분기하면 된다. (현재는 로컬 테이블 조회만)</p>
     */
    @Transactional(readOnly = true)
    public List<RuleCandidate> loadRules(String policyId, String difficulty) {
        List<BpmRoleAssignRule> rules = query(policyId, difficulty);

        List<RuleCandidate> result = new ArrayList<>();
        for (BpmRoleAssignRule r : rules) {
            double w = r.getWeight() != null ? r.getWeight() : 0d;
            result.add(new RuleCandidate(r.getEndpoint(), r.getDifficulty(), w));
        }
        return result;
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
