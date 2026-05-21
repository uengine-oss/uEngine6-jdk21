package org.uengine.five.dto;

import java.util.Date;

/**
 * 부재자/대결자 신규 등록 요청 DTO.
 *
 * <p>서버가 관리하는 필드(absenceId / status / createdDate / terminationDate)는
 * 의도적으로 노출하지 않습니다. 클라이언트가 임의로 세팅하더라도 매핑 단계에서 폐기됩니다.</p>
 */
public class AbsenceCreateRequest {

    /** 부재자 사용자 ID */
    private String userId;
    /** 부재자 표시명 */
    private String userName;
    /** 대결자 사용자 ID */
    private String agentUserId;
    /** 대결자 표시명 */
    private String agentUserNm;
    /** 부재 시작일 (필수) */
    private Date startDate;
    /** 부재 종료일 (null 이면 종료일 미정으로 등록되어 수동 종료까지 유지) */
    private Date endDate;

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
}
