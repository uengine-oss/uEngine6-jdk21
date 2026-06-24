package org.uengine.hwlife.search.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 나의 할일 검색 요청 — POST /search/my-todo JSON body.
 */
public class MyTodoRequest {

    private String bswrClsfCode;
    private String custNo;
    private String fncgBswrDvsnCode;
    private String loanCntcNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String trcTag;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date arrvStarDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date arrvEndDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date loanHopeStarDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date loanHopeEndDate;

    private String group;
    private String sortOrdrVal;
    private String endpoint;
    private String pageNo;

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public String getCustNo() {
        return custNo;
    }

    public void setCustNo(String custNo) {
        this.custNo = custNo;
    }

    public String getFncgBswrDvsnCode() {
        return fncgBswrDvsnCode;
    }

    public void setFncgBswrDvsnCode(String fncgBswrDvsnCode) {
        this.fncgBswrDvsnCode = fncgBswrDvsnCode;
    }

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
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

    public String getTrcTag() {
        return trcTag;
    }

    public void setTrcTag(String trcTag) {
        this.trcTag = trcTag;
    }

    public Date getArrvStarDate() {
        return arrvStarDate;
    }

    public void setArrvStarDate(Date arrvStarDate) {
        this.arrvStarDate = arrvStarDate;
    }

    public Date getArrvEndDate() {
        return arrvEndDate;
    }

    public void setArrvEndDate(Date arrvEndDate) {
        this.arrvEndDate = arrvEndDate;
    }

    public Date getLoanHopeStarDate() {
        return loanHopeStarDate;
    }

    public void setLoanHopeStarDate(Date loanHopeStarDate) {
        this.loanHopeStarDate = loanHopeStarDate;
    }

    public Date getLoanHopeEndDate() {
        return loanHopeEndDate;
    }

    public void setLoanHopeEndDate(Date loanHopeEndDate) {
        this.loanHopeEndDate = loanHopeEndDate;
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

    public String getPageNo() {
        return pageNo;
    }

    public void setPageNo(String pageNo) {
        this.pageNo = pageNo;
    }
}
