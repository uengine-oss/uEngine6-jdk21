package org.uengine.five.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 나의 결재함(My Approval) 검색 조건 DTO.
 *
 * <p>{@code GET /worklist/search/findMyApproval} 의 쿼리스트링을 자동 바인딩한다.
 * 모든 필드는 옵션이며, null/미전달 시 해당 조건은 Repository JPQL 에서 무시된다.</p>
 */
public class ApprovalWorklistRequest {

    /** 담당자 (자기 결재함 한정). */
    private String endpoint;

    /** 업무구분 (프로세스 정의 ID). */
    private String defId;

    /** 단위업무 식별자 — Repository 내부에서 {@code wl.absTrcTag} 와 매칭. */
    private String activityId;

    /** 업무 상태. */
    private String status;

    /** 시작일자 (이 날짜 이후, inclusive). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date startDate;

    /** 종료일자 (이 날짜 이전, inclusive — NULL 종료일은 제외). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endDate;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDefId() {
        return defId;
    }

    public void setDefId(String defId) {
        this.defId = defId;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
