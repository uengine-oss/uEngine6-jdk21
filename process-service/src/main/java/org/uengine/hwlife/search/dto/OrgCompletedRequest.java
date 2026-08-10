package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 조직 완료 건 검색 요청 — POST /search/org-completed JSON body.
 */
public class OrgCompletedRequest {

    private String bpmBswrClsfCode;
    private String fncgBswrDvsnCode;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstStarDttm;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstEndDttm;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String loanCntcNo;
    private String custId;
    private String fncgWndwOrgnCode;
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
