package org.uengine.hwlife.rule.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * 규칙 기반 담당자 배정 규칙.
 *
 * <p>정책(POLICY_ID) + 난이도(DIFFICULTY) 별로 배정 후보 담당자(ENDPOINT)와
 * 목표 부하 비중(WEIGHT)을 정의한다. 외부 역량 기준정보(ESB)에서
 * 정책(POLICY_ID) 단위로 ESB에서 동기화되며, SYNCED_AT 으로 적재 시각을 추적한다.
 * (policyId, difficulty)에 활성 처리자가 없을 때만 외부 동기화를 수행한다.</p>
 */
@Entity
@Table(name = "BPM_ROLE_ASSIGN_RULE")
@SequenceGenerator(
        name = "role_assign_rule_seq_gen",
        sequenceName = "SEQ_BPM_ROLE_ASSIGN_RULE",
        allocationSize = 50
)
public class BpmRoleAssignRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_assign_rule_seq_gen")
    private Long ruleId;

    private String policyId;

    private String difficulty;

    private String endpoint;

    private Integer weight;

    /**
     * 사용 여부 (boolean).
     * ESB Y/N 적재 시 Y=true, N=false 로 저장하고, 후보 조회 시에는 반대로 해석한다
     * (true=활성, false=비활성).
     */
    private Boolean useYn;

    private Date syncedAt;

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Boolean getUseYn() {
        return useYn;
    }

    public void setUseYn(Boolean useYn) {
        this.useYn = useYn;
    }

    public Date getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Date syncedAt) {
        this.syncedAt = syncedAt;
    }
}
