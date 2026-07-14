package org.uengine.hwlife.search.dto;

import java.time.LocalDateTime;

/**
 * 조직 진행 건 검색 요청 — POST /search/org-running JSON body.
 */
public class OrgRunningRequest {

    private String bswrClsfCode;
    private String fncgVswrDvsnCode;
    private String fncgBpmTaskTrcgNm;
    private LocalDateTime rqstStarDttm;
    private LocalDateTime rqstEndDttm;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String loanCntcNo;
    private String custNo;
    private String fncgWndwOrgnCode;
    private String rqstDvsnCode;
    private String sortOrdrVal;

    private Integer pageNo;

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public String getFncgVswrDvsnCode() {
        return fncgVswrDvsnCode;
    }

    public void setFncgVswrDvsnCode(String fncgVswrDvsnCode) {
        this.fncgVswrDvsnCode = fncgVswrDvsnCode;
    }

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }

    public LocalDateTime getRqstStarDttm() {
        return rqstStarDttm;
    }

    public void setRqstStarDttm(LocalDateTime rqstStarDttm) {
        this.rqstStarDttm = rqstStarDttm;
    }

    public LocalDateTime getRqstEndDttm() {
        return rqstEndDttm;
    }

    public void setRqstEndDttm(LocalDateTime rqstEndDttm) {
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

    public String getCustNo() {
        return custNo;
    }

    public void setCustNo(String custNo) {
        this.custNo = custNo;
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

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }
}
