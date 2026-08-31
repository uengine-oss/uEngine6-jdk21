package org.uengine.hwlife.overriding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.overriding.JPAProcessInstance;
import org.uengine.hwlife.rule.RuleRoleResolutionService;
import org.uengine.hwlife.rule.dto.RuleCandidate;
import org.uengine.hwlife.rule.RuleRoleResolutionSupport;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.Role;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.RoleResolutionContext;

/**
 * 역량 기준정보 기반 가중치 라운드로빈 담당자 배정.
 *
 * <p>정책 ID({@code policyId})는 모델러 {@link #setPolicyId(String)} 만 사용한다 (인스턴스 변수 없음).
 * ESB 역량 기준정보 조회 시 {@code cpabNm} 파라미터로 전달된다.</p>
 *
 * <p>인스턴스 변수:</p>
 * <ul>
 *   <li>{@code cpabLvdfLvelNm} → difficulty (없으면 {@link #DEFAULT_DIFFICULTY})</li>
 * </ul>
 * <p>{@code refId} 는 실행 중 인스턴스 {@code corrKey}(대출프로세스관리번호)를 사용한다.</p>
 */
public class RuleBasedRoleResolutionContext extends RoleResolutionContext {

    private static final long serialVersionUID = GlobalContext.SERIALIZATION_UID;

    public static final String VAR_DIFFICULTY = "cpabLvdfLvelNm";
    public static final String VAR_REF_ID = "corrKey";
    public static final String DEFAULT_DIFFICULTY = "LOW";

    /** 모델러에서 지정하는 정책 ID(= ESB cpabNm). 필수. */
    private String policyId;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    @Override
    public RoleMapping getActualMapping(ProcessDefinition pd, ProcessInstance instance,
                                        String tracingTag, Map options) throws Exception {
        RuleRoleResolutionService service = RuleRoleResolutionSupport.get();

        // 1) policyId(context), difficulty(cpabLvdfLvelNm), refId(instance.corrKey)
        if (!isNotEmpty(policyId)) {
            throw new IllegalStateException(
                    "RuleBasedRoleResolutionContext: context.policyId(정책ID) 가 필요합니다.");
        }
        String difficulty = resolveDifficulty(instance, tracingTag);
        String refId = resolveRefId(instance, tracingTag);
        if (!isNotEmpty(refId)) {
            throw new IllegalStateException(
                    "RuleBasedRoleResolutionContext: instance.corrKey(대출프로세스관리번호) 가 필요합니다.");
        }

        // 2) 역량 기준정보 — 오늘 동기화분 DB 사용, 아니면 ESB 동기화
        List<RuleCandidate> rules = syncRoleAssignRules(policyId, difficulty);
        if (rules.isEmpty()) {
            throw new IllegalStateException("RuleBasedRoleResolutionContext: 정책 " + policyId
                    + " / 난이도 " + difficulty + " 에 대한 배정 규칙이 없습니다.");
        }

        // 3) refId 동일 이력의 이전 처리자 제외
        Set<String> excluded = service.findExcludedEndpointsByRefId(refId);
        List<RuleCandidate> eligibleRules = excludeRules(rules, excluded);
        if (eligibleRules.isEmpty()) {
            throw new IllegalStateException("RuleBasedRoleResolutionContext: refId " + refId
                    + " 에서 제외 후 배정 가능한 처리자가 없습니다.");
        }

        // 4) 후보 선정 + 업무량 조회 (전월 1일~오늘, 진행중+완료)
        List<String> candidates = selectByAssignee(eligibleRules);
        Map<String, Integer> remaining = service.queryWorkload(refId, candidates);

        // 5) GAP(가중치 − 업무량) 기반 담당자 결정
        String chosen = selectByGap(eligibleRules, remaining);

        // 6) 부재 시 대결자로 대체
        String assignee = service.resolveAbsentAssignee(chosen);

        // 7) RoleMapping 생성 + BPM_ROLEMAPPING 메타 적재
        RoleMapping mapping = RoleMapping.create();
        mapping.setEndpoint(assignee);
        mapping.setAssignType(Role.ASSIGNTYPE_USER);
        mapping.setDispatchingOption(Role.DISPATCHINGOPTION_LOADBALANCED);
        mapping.fill();
        saveMapping(mapping, policyId, difficulty, refId);
        service.persistRoleMapping(instance, mapping, policyId, difficulty, refId);

        return mapping;
    }

    String resolveDifficulty(ProcessInstance instance, String tracingTag) throws Exception {
        String difficulty = readVar(instance, tracingTag, VAR_DIFFICULTY);
        return isNotEmpty(difficulty) ? difficulty : DEFAULT_DIFFICULTY;
    }

    /** 실행 중 인스턴스 엔티티의 corrKey. 프로세스 변수가 아닌 인스턴스 메타데이터. */
    String resolveRefId(ProcessInstance instance, String tracingTag) throws Exception {
        if (instance != null) {
            ProcessInstance local = instance.getLocalInstance();
            if (local instanceof JPAProcessInstance jpa) {
                ProcessInstanceEntity entity = jpa.getProcessInstanceEntity();
                if (entity != null && isNotEmpty(entity.getCorrKey())) {
                    return entity.getCorrKey();
                }
            }
        }
        return readVar(instance, tracingTag, VAR_REF_ID);
    }

    List<RuleCandidate> syncRoleAssignRules(String policyId, String difficulty) {
        return RuleRoleResolutionSupport.get().syncRoleAssignRules(policyId, difficulty);
    }

    List<RuleCandidate> excludeRules(List<RuleCandidate> rules, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return rules;
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (RuleCandidate r : rules) {
            if (!excluded.contains(r.getEndpoint())) {
                result.add(r);
            }
        }
        return result;
    }

    List<String> selectByAssignee(List<RuleCandidate> rules) {
        List<String> endpoints = new ArrayList<>();
        for (RuleCandidate r : rules) {
            if (isNotEmpty(r.getEndpoint()) && !endpoints.contains(r.getEndpoint())) {
                endpoints.add(r.getEndpoint());
            }
        }
        return endpoints;
    }

    String selectByGap(List<RuleCandidate> rules, Map<String, Integer> remaining) {
        String best = null;
        double bestGap = -Double.MAX_VALUE;
        int bestLoad = Integer.MAX_VALUE;

        for (RuleCandidate r : rules) {
            String ep = r.getEndpoint();
            if (!isNotEmpty(ep)) {
                continue;
            }
            int load = remaining.getOrDefault(ep, 0);
            double gap = r.getWeight() - load;
            if (gap > bestGap || (gap == bestGap && load < bestLoad)) {
                best = ep;
                bestGap = gap;
                bestLoad = load;
            }
        }

        if (best == null) {
            throw new IllegalStateException("RuleBasedRoleResolutionContext: 배정 가능한 담당자를 결정하지 못했습니다.");
        }
        return best;
    }

    void saveMapping(RoleMapping mapping, String policyId, String difficulty, String refId) {
        if (isNotEmpty(policyId)) {
            mapping.setExtendedProperty("policyId", policyId);
        }
        if (isNotEmpty(difficulty)) {
            mapping.setExtendedProperty("difficulty", difficulty);
        }
        if (isNotEmpty(refId)) {
            mapping.setExtendedProperty("refId", refId);
        }
    }

    @Override
    public String getDisplayName() {
        return isNotEmpty(policyId)
                ? "Rule-based assignee for policy '" + policyId + "'"
                : "Rule-based assignee (context.policyId required)";
    }

    private static String readVar(ProcessInstance instance, String tracingTag, String key) throws Exception {
        if (instance == null || !isNotEmpty(key)) {
            return null;
        }
        Object v = instance.get("", key);
        if (v == null) {
            v = instance.getProperty(tracingTag, key);
        }
        return v != null ? String.valueOf(v) : null;
    }

    private static boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
