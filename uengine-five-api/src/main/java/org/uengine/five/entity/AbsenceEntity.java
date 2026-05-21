package org.uengine.five.entity;

import jakarta.persistence.*;

import java.util.Date;

/**
 * 부재자(Absence) / 대결자(Agent) 설정 엔티티.
 *
 * <p>특정 사용자(USER_ID)가 부재중일 때, 그 사용자의 업무를 대신 수행할
 * 대결자(AGENT_USER_ID)를 기간 단위로 매핑합니다. STATUS = ACTIVE 이고
 * 현재 시각이 START_DATE ~ END_DATE 사이인 row 가 실제 라우팅에 사용됩니다.</p>
 */
@Entity
@Table(name = "BPM_ABSENCE_TB")
@SequenceGenerator(
        name = "absence_seq_gen",
        sequenceName = "SEQ_BPM_ABSENCE",
        allocationSize = 1
)
public class AbsenceEntity {

    /** STATUS: 등록되어 라우팅 대상이 되는 활성 상태 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** STATUS: 운영자/사용자 요청으로 조기 종료된 상태 (soft delete) */
    public static final String STATUS_TERMINATED = "TERMINATED";
    /** STATUS: END_DATE 가 지나 자연 만료된 상태 (배치/스케줄러 갱신용) */
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "absence_seq_gen")
    @Column(name = "ABSENCE_ID")
    private Long absenceId;

    /** 부재자 사용자 ID (Keycloak/IAM principal.userId 와 동일한 형식) */
    @Column(name = "USER_ID", nullable = false, length = 255)
    private String userId;

    /** 부재자 표시명 (UI 표시용 스냅샷) */
    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String userName;

    /** 대결자(Agent) 사용자 ID */
    @Column(name = "AGENT_USER_ID", nullable = false, length = 255)
    private String agentUserId;

    /** 대결자 표시명 (UI 표시용 스냅샷) */
    @Column(name = "AGENT_USER_NM", nullable = false, length = 100)
    private String agentUserNm;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "START_DATE", nullable = false)
    private Date startDate;

    /** NULL 인 경우 종료일 미정 (수동 종료 전까지 유지) */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "END_DATE")
    private Date endDate;

    /** {@link #STATUS_ACTIVE}, {@link #STATUS_TERMINATED}, {@link #STATUS_EXPIRED} */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATED_DATE", nullable = false, updatable = false)
    private Date createdDate;

    /** soft delete 시각 (STATUS = TERMINATED/EXPIRED 와 함께 세팅) */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "TERMINATION_DATE")
    private Date terminationDate;

    @PrePersist
    void onCreate() {
        if (createdDate == null) {
            createdDate = new Date();
        }
        if (status == null || status.isEmpty()) {
            status = STATUS_ACTIVE;
        }
    }

    public Long getAbsenceId() {
        return absenceId;
    }

    public void setAbsenceId(Long absenceId) {
        this.absenceId = absenceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(String agentUserId) {
        this.agentUserId = agentUserId;
    }

    public String getAgentUserNm() {
        return agentUserNm;
    }

    public void setAgentUserNm(String agentUserNm) {
        this.agentUserNm = agentUserNm;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(Date terminationDate) {
        this.terminationDate = terminationDate;
    }
}
