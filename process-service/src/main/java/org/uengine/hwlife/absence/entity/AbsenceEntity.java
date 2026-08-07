package org.uengine.hwlife.absence.entity;

import jakarta.persistence.*;

import java.util.Date;

/**
 * 한화생명 융자차세대 - 부재자(Absence) / 대결자(Agent) 설정 엔티티.
 *
 * <p>특정 사용자(USER_ID)가 부재중일 때, 그 사용자의 업무를 대신 수행할
 * 대결자(AGENT_USER_ID)를 기간 단위로 매핑합니다. ABSC_CNCE_DTTM 이 NULL 이고
 * 현재 시각이 ABSC_STAR_DTTM ~ ABSC_END_DTTM 사이인 row 가 실제 라우팅에 사용됩니다.</p>
 */
@Entity
@Table(name = "BPM_ABSENCE")
@SequenceGenerator(
        name = "absence_seq_gen",
        sequenceName = "SEQ_BPM_ABSENCE",
        allocationSize = 50
)
public class AbsenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "absence_seq_gen")
    private Long abseId;

    private String userId;

    private String agentUserId;
    private String agentGroupCd;

    private Date abscStarDttm;
    private Date abscEndDttm;

    @Column(name = "ABSC_CNCE_DTTM")
    private Date abscRscsDttm;
    @Column(name = "ABSC_CRET_DTTM")
    private Date abscStupDttm;

    @PrePersist
    void onCreate() {
        if (abscStupDttm == null) {
            abscStupDttm = new Date();
        }
    }

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

    public String getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(String agentUserId) {
        this.agentUserId = agentUserId;
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

    public Date getAbscRscsDttm() {
        return abscRscsDttm;
    }

    public void setAbscRscsDttm(Date abscRscsDttm) {
        this.abscRscsDttm = abscRscsDttm;
    }

    public Date getAbscCretDttm() {
        return abscStupDttm;
    }

    public void setAbscStupDttm(Date abscStupDttm) {
        this.abscStupDttm = abscStupDttm;
    }
}
