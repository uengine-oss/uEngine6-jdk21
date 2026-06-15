package org.uengine.hwlife.rule;

/**
 * 배정 규칙 1건의 후보 담당자 정보 (BPM_ROLE_ASSIGN_RULE 의 런타임 표현).
 *
 * <p>RoleResolutionContext(POJO)와 Service 사이에서 주고받는 순수 값 객체.
 * 엔티티({@code BpmRoleAssignRule})를 그대로 노출하지 않기 위해 분리한다.</p>
 */
public class RuleCandidate {

    private final String endpoint;
    private final String difficulty;
    private final double weight;

    public RuleCandidate(String endpoint, String difficulty, double weight) {
        this.endpoint = endpoint;
        this.difficulty = difficulty;
        this.weight = weight;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getDifficulty() {
        return difficulty;
    }

    /** 목표 부하 비중. GAP 계산(weight - 현재 진행건수)의 기준값. */
    public double getWeight() {
        return weight;
    }
}
