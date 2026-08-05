package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 조직 진행 건 검색 요청 — POST /search/org-running JSON body.
 * - 요청기관: 해당 업무를 최초로 시작한 기관 (rqstDvsnCode == Y AND fncgWndwOrgnCode == bpm_procinst.init_group_cd )
 * - 진행기관: 현재 단위업무를 진행 하고 있는 기관 (rqstDvsnCode == N AND fncgWndwOrgnCode == bpm_procinst.curr_group_cd)
 * - fncgWndwOrgnCode 가 없으면 header.belnOrgnCode 사용, 둘 다 없으면 빈 목록 응답
 * - rqstDvsnCode 기본값 N, pageSize 기본값 20, 조회기간 기본값 (오늘-30일)~오늘
 */
public class OrgRunningRequest {

    private String bpmBswrClsfCode;
    private String fncgBswrDvsnCode;
    private String fncgBpmTaskTrcgNm;

    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstStarDttm;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstEndDttm;

    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String loanCntcNo;
    private String custId;
    private String fncgWndwOrgnCode;
    private String rqstDvsnCode;
    private String sortOrdrVal;

    private String nextKey;
    private Integer pageSize;

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }

    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
    }

    public String getFncgBswrDvsnCode() {
        return fncgBswrDvsnCode;
    }

    public void setFncgBswrDvsnCode(String fncgBswrDvsnCode) {
        this.fncgBswrDvsnCode = fncgBswrDvsnCode;
    }

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }

    public Date getRqstStarDttm() {
        return rqstStarDttm;
    }

    public void setRqstStarDttm(Date rqstStarDttm) {
        this.rqstStarDttm = rqstStarDttm;
    }

    public Date getRqstEndDttm() {
        return rqstEndDttm;
    }

    public void setRqstEndDttm(Date rqstEndDttm) {
        this.rqstEndDttm = rqstEndDttm;
    }

    public String getFncgSuptTrgtDvsnCode() {
        return fncgSuptTrgtDvsnCode;
    }

    public void setFncgSuptTrgtDvsnCode(String fncgSuptTrgtDvsnCode) {
        this.fncgSuptTrgtDvsnCode = fncgSuptTrgtDvsnCode;
    }

    public String getLoanSubjDvsnCode() {
        return loanSubjDvsnCode;
    }

    public void setLoanSubjDvsnCode(String loanSubjDvsnCode) {
        this.loanSubjDvsnCode = loanSubjDvsnCode;
    }

    public String getFncgMneyUsagClsfCode() {
        return fncgMneyUsagClsfCode;
    }

    public void setFncgMneyUsagClsfCode(String fncgMneyUsagClsfCode) {
        this.fncgMneyUsagClsfCode = fncgMneyUsagClsfCode;
    }

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
    }

    public String getFncgWndwOrgnCode() {
        return fncgWndwOrgnCode;
    }

    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) {
        this.fncgWndwOrgnCode = fncgWndwOrgnCode;
    }

    public String getRqstDvsnCode() {
        return rqstDvsnCode;
    }

    public void setRqstDvsnCode(String rqstDvsnCode) {
        this.rqstDvsnCode = rqstDvsnCode;
    }

    public String getSortOrdrVal() {
        return sortOrdrVal;
    }

    public void setSortOrdrVal(String sortOrdrVal) {
        this.sortOrdrVal = sortOrdrVal;
    }

    public String getNextKey() {
        return nextKey;
    }

    public void setNextKey(String nextKey) {
        this.nextKey = nextKey;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
