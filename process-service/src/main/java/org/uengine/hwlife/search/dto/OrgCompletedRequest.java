package org.uengine.hwlife.search.dto;

import java.time.LocalDateTime;

/**
 * 조직 완료 건 검색 요청 — POST /search/org-completed JSON body.
 */
public class OrgCompletedRequest {

    private String bswrClsfCode;
    private String fncgVswrDvsnCode;
    private LocalDateTime rqstStarDttm;
    private LocalDateTime rqstEndDttm;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String loanCntcNo;
    private String custNo;
    private String fncgWndwOrgnCode;
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
