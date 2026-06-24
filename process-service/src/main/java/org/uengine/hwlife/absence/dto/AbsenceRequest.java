package org.uengine.hwlife.absence.dto;

import java.util.Date;

/**
 * 부재 설정/해제 통합 요청.
 *
 * <p>{@code bswrDvsnVal}: {@link #BSWR_DVSN_REG} 설정, {@link #BSWR_DVSN_RLS} 해제.</p>
 */
public class AbsenceRequest {

    public static final String BSWR_DVSN_REG = "0";
    public static final String BSWR_DVSN_RLS = "1";

    /** 업무구분값 — 0(설정) / 1(해제) */
    private String bswrDvsnVal;

    /** 해제 시 필수, 설정 시 미사용 */
    private Long abseId;

    private String userId;
    private String userName;
    private String agentUserId;
    private String agentUserName;
    private String agentGroupCd;
    private Date abscStarDttm;
    private Date abscEndDttm;

    public String getBswrDvsnVal() {
        return bswrDvsnVal;
    }

    public void setBswrDvsnVal(String bswrDvsnVal) {
        this.bswrDvsnVal = bswrDvsnVal;
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
}
