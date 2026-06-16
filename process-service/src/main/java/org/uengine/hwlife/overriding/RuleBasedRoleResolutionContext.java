package org.uengine.hwlife.overriding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.uengine.hwlife.rule.RuleCandidate;
import org.uengine.hwlife.rule.RuleRoleResolutionService;
import org.uengine.hwlife.rule.RuleRoleResolutionSupport;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.Role;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.RoleResolutionContext;

/**
 * <p>흐름(시퀀스 다이어그램 대응):</p>
 * <ol>
 *   <li>인스턴스에서 POLICY_ID / DIFFICULTY / REF_ID 확보</li>
 *   <li>{@link #loadRules} : 규칙 조회(없으면 외부 적재 후 재조회)</li>
 *   <li>{@link #selectByAssignee} : REF_ID 기준 후보 담당자 선별</li>
 *   <li>queryWorkload : 후보별 진행중 업무량 조회</li>
 *   <li>{@link #selectByGap} : GAP 계산 후 담당자 결정</li>
 *   <li>RoleMapping 생성 + {@link #saveMapping} 메타 적재</li>
 * </ol>
 */
public class RuleBasedRoleResolutionContext extends RoleResolutionContext {

    private static final long serialVersionUID = GlobalContext.SERIALIZATION_UID;

    /** 모델러에서 세팅되는 인스턴스 변수 키. */
    public static final String VAR_POLICY_ID = "POLICY_ID";
    public static final String VAR_DIFFICULTY = "DIFFICULTY";
    public static final String VAR_REF_ID = "REF_ID";

    /** 모델러에서 직접 지정하는 정책 ID. 비어 있으면 인스턴스 변수 POLICY_ID 로 대체. */
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
        // 1) 기준정보 확보 (POLICY_ID 는 context 우선, 없으면 인스턴스 변수)
        String resolvedPolicyId = isNotEmpty(policyId) ? policyId : readVar(instance, tracingTag, VAR_POLICY_ID);
        String difficulty = readVar(instance, tracingTag, VAR_DIFFICULTY);
        String refId = readVar(instance, tracingTag, VAR_REF_ID);

        if (!isNotEmpty(resolvedPolicyId)) {
            throw new IllegalStateException(
                    "RuleBasedRoleResolutionContext: POLICY_ID 가 필요합니다 (context.policyId 또는 인스턴스 변수 POLICY_ID).");
        }

        RuleRoleResolutionService service = RuleRoleResolutionSupport.get();

        // 2) 배정 규칙 조회 (없으면 외부 기준정보에서 적재 후 재조회)
        List<RuleCandidate> rules = loadRules(resolvedPolicyId, difficulty);
        if (rules.isEmpty()) {
            throw new IllegalStateException("RuleBasedRoleResolutionContext: 정책 " + resolvedPolicyId
                    + " / 난이도 " + difficulty + " 에 대한 배정 규칙이 없습니다.");
        }

        // 3) REF_ID 기준 후보 담당자 선별
        List<String> candidates = selectByAssignee(rules, refId);

        // 4) 후보별 진행중 업무량 조회
        Map<String, Integer> remaining = service.queryWorkload(refId, candidates);

        // 5) GAP 계산으로 담당자 결정
        String chosen = selectByGap(rules, remaining);

        // 6) RoleMapping 생성
        RoleMapping mapping = RoleMapping.create();
        mapping.setEndpoint(chosen);
        mapping.setAssignType(Role.ASSIGNTYPE_USER);

        // 7) 배정 메타(정책/난이도/REF_ID) 적재 → 코어가 BPM_ROLEMAPPING 저장 시 함께 보존
        saveMapping(mapping, resolvedPolicyId, difficulty, refId);

        return mapping;
    }

    /** 배정 규칙 조회 — 실제 DB/외부 호출은 서비스가 담당. */
    List<RuleCandidate> loadRules(String policyId, String difficulty) {
        return RuleRoleResolutionSupport.get().loadRules(policyId, difficulty);
    }

    /**
     * REF_ID 기준 후보 담당자(endpoint) 목록 산출 (순수 로직).
     *
     * <p>규칙에 정의된 eligible 담당자 전체를 중복 없이 후보로 반환한다.
     * REF_ID 연속성(직전 담당자 가중)은 업무량 조회 단계에서 반영한다.</p>
     */
    List<String> selectByAssignee(List<RuleCandidate> rules, String refId) {
        List<String> endpoints = new ArrayList<>();
        for (RuleCandidate r : rules) {
            if (isNotEmpty(r.getEndpoint()) && !endpoints.contains(r.getEndpoint())) {
                endpoints.add(r.getEndpoint());
            }
        }
        return endpoints;
    }

    /**
     * GAP(목표 WEIGHT 대비 현재 진행 업무량) 최대 담당자 선정 (순수 로직).
     *
     * <p>gap = weight - 현재 진행건수. 값이 클수록(=여유가 많을수록) 우선.
     * 동률이면 현재 부하가 적은 담당자, 그래도 동률이면 규칙 정의 순서.</p>
     */
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

    /**
     * 결정된 매핑에 정책/난이도/REF_ID 메타를 실어, 코어가 BPM_ROLEMAPPING 저장 시 함께 보존하도록 한다.
     * (별도 이력 테이블이 필요하면 서비스에 saveAssignment 를 추가해 확장)
     */
    void saveMapping(RoleMapping mapping, String policyId, String difficulty, String refId) {
        if (isNotEmpty(policyId)) {
            mapping.setExtendedProperty(VAR_POLICY_ID, policyId);
        }
        if (isNotEmpty(difficulty)) {
            mapping.setExtendedProperty(VAR_DIFFICULTY, difficulty);
        }
        if (isNotEmpty(refId)) {
            mapping.setExtendedProperty(VAR_REF_ID, refId);
        }
    }

    @Override
    public String getDisplayName() {
        return isNotEmpty(policyId)
                ? "Rule-based assignee for policy '" + policyId + "'"
                : "Rule-based assignee (policy from instance variable)";
    }

    private static String readVar(ProcessInstance instance, String tracingTag, String key) throws Exception {
        if (instance == null) {
            return null;
        }
        Object v = instance.getProperty(tracingTag, key);
        return v != null ? String.valueOf(v) : null;
    }

    private static boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
