package org.uengine.hwlife.search.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 나의 진행 검색 요청 — POST /search/my-progress JSON body.
 */
public class MyProgressRequest {

    private String bswrClsfCode;
    private String fncgBswrDvsnCode;
    private String trcTag;
    private Date rqstStarDate;
    private Date rqstEndDate;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String custId;
    private String loanCntcNo;
    private String group;
    private String sortOrdrVal;
    private String endpoint;

    private Integer pageNo;

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public String getFncgBswrDvsnCode() {
        return fncgBswrDvsnCode;
    }

    public void setFncgBswrDvsnCode(String fncgBswrDvsnCode) {
        this.fncgBswrDvsnCode = fncgBswrDvsnCode;
    }

    public String getTrcTag() {
        return trcTag;
    }

    public void setTrcTag(String trcTag) {
        this.trcTag = trcTag;
    }

    public Date getRqstStarDate() {
        return rqstStarDate;
    }

    public void setRqstStarDate(Date rqstStarDate) {
        this.rqstStarDate = rqstStarDate;
    }

    public Date getRqstEndDate() {
        return rqstEndDate;
    }

    public void setRqstEndDate(Date rqstEndDate) {
        this.rqstEndDate = rqstEndDate;
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

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
    }

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSortOrdrVal() {
        return sortOrdrVal;
    }

    public void setSortOrdrVal(String sortOrdrVal) {
        this.sortOrdrVal = sortOrdrVal;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }
}
