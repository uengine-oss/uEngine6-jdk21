package org.uengine.hwlife.rule.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;

/**
 * 한화생명 융자차세대 - 규칙 기반 담당자 배정 규칙.
 *
 * <p>정책(POLICY_ID) + 난이도(DIFFICULTY) 별로 배정 후보 담당자(ENDPOINT)와
 * 목표 부하 비중(WEIGHT)을 정의한다. 외부 기준정보({@code ExternalPolicyApi})에서
 * 동기화되며 SYNCED_AT 으로 적재 시각을 추적한다.</p>
 */
@Entity
@Table(
        name = "BPM_ROLE_ASSIGN_RULE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_BPM_ROLE_ASSIGN_RULE_01",
                        columnNames = {"POLICY_ID", "DIFFICULTY", "ENDPOINT"})
        })
@SequenceGenerator(
        name = "role_assign_rule_seq_gen",
        sequenceName = "SEQ_BPM_ROLE_ASSIGN_RULE",
        allocationSize = 1
)
public class BpmRoleAssignRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_assign_rule_seq_gen")
    @Column(name = "RULE_ID")
    private Long ruleId;

    @Column(name = "POLICY_ID")
    private String policyId;

    @Column(name = "DIFFICULTY")
    private String difficulty;

    @Column(name = "ENDPOINT")
    private String endpoint;

    @Column(name = "WEIGHT")
    private Double weight;

    /** 사용 여부 ('Y'/'N'). 활성 규칙만 후보로 사용. */
    @Column(name = "USE_YN", length = 1)
    private String useYn;

    @Column(name = "SYNCED_AT")
    @Temporal(TemporalType.TIMESTAMP)
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

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public Date getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Date syncedAt) {
        this.syncedAt = syncedAt;
    }
}
