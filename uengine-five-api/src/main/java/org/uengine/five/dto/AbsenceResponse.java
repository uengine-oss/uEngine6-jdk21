package org.uengine.five.dto;

import java.util.Date;

import org.uengine.five.entity.AbsenceEntity;

/**
 * 부재자/대결자 설정 응답 DTO.
 *
 * <p>{@link AbsenceEntity} 의 모든 컬럼을 평탄화하여 노출합니다. Entity 를 직접 직렬화하지 않도록
 * 서비스 응답 경계에서 {@link #from(AbsenceEntity)} 로 변환하세요.</p>
 */
public class AbsenceResponse {

    private Long absenceId;
    private String userId;
    private String userName;
    private String agentUserId;
    private String agentUserNm;
    private Date startDate;
    private Date endDate;
    private String status;
    private Date createdDate;
    private Date terminationDate;

    /** Entity → Response 변환. null 입력은 null 반환. */
    public static AbsenceResponse from(AbsenceEntity e) {
        if (e == null) return null;
        AbsenceResponse r = new AbsenceResponse();
        r.absenceId = e.getAbsenceId();
        r.userId = e.getUserId();
        r.userName = e.getUserName();
        r.agentUserId = e.getAgentUserId();
        r.agentUserNm = e.getAgentUserNm();
        r.startDate = e.getStartDate();
        r.endDate = e.getEndDate();
        r.status = e.getStatus();
        r.createdDate = e.getCreatedDate();
        r.terminationDate = e.getTerminationDate();
        return r;
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
