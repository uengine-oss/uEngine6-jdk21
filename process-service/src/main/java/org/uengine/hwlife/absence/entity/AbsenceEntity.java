package org.uengine.hwlife.absence.entity;

import jakarta.persistence.*;

import java.util.Date;

/**
 * 한화생명 융자차세대 - 부재자(Absence) / 대결자(Agent) 설정 엔티티.
 *
 * <p>특정 사용자(USER_ID)가 부재중일 때, 그 사용자의 업무를 대신 수행할
 * 대결자(AGENT_USER_ID)를 기간 단위로 매핑합니다. ABSC_TERMINATE_DTTM 이 NULL 이고
 * 현재 시각이 ABSC_STAR_DTTM ~ ABSC_END_DTTM 사이인 row 가 실제 라우팅에 사용됩니다.</p>
 */
@Entity
@Table(name = "BPM_ABSENCE_TB")
@SequenceGenerator(
        name = "absence_seq_gen",
        sequenceName = "SEQ_BPM_ABSENCE",
        allocationSize = 1
)
public class AbsenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "absence_seq_gen")
    @Column(name = "ABSE_ID")
    private Long abseId;

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
    @Column(name = "AGENT_USER_NAME", nullable = false, length = 100)
    private String agentUserName;

    /** 대결자 그룹 코드 */
    @Column(name = "AGENT_GROUP_CD", length = 50)
    private String agentGroupCd;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ABSC_STAR_DTTM", nullable = false)
    private Date abscStarDttm;

    /** NULL 인 경우 종료일 미정 (수동 종료 전까지 유지) */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ABSC_END_DTTM")
    private Date abscEndDttm;

    /** 조기 종료(해제) 시각. NULL 이면 활성 부재 */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ABSC_TERMINATE_DTTM")
    private Date abscTerminateDttm;

    public Long getAbseId() {
        return abseId;
    }

    public void setAbseId(Long abseId) {
        this.abseId = abseId;
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

    public String getAgentUserName() {
        return agentUserName;
    }

    public void setAgentUserName(String agentUserName) {
        this.agentUserName = agentUserName;
    }

    public String getAgentGroupCd() {
        return agentGroupCd;
    }

    public void setAgentGroupCd(String agentGroupCd) {
        this.agentGroupCd = agentGroupCd;
    }

    public Date getAbscStarDttm() {
        return abscStarDttm;
    }

    public void setAbscStarDttm(Date abscStarDttm) {
        this.abscStarDttm = abscStarDttm;
    }

    public Date getAbscEndDttm() {
        return abscEndDttm;
    }

    public void setAbscEndDttm(Date abscEndDttm) {
        this.abscEndDttm = abscEndDttm;
    }

    public Date getAbscTerminateDttm() {
        return abscTerminateDttm;
    }

    public void setAbscTerminateDttm(Date abscTerminateDttm) {
        this.abscTerminateDttm = abscTerminateDttm;
    }
}
