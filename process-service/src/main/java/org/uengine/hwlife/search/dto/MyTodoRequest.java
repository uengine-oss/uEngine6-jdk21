package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 나의 할일 검색 요청 — POST /search/my-todo JSON body.
 */
public class MyTodoRequest {

    private String bswrClsfCode;
    private String custId;
    private String fncgBswrDvsnCode;
    private String loanCntcNo;
    private String loanPcesMgmtNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String fncgBpmTaskTrcgNm;

    @JsonAlias("starDate")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endDate;

    @JsonAlias("hopeStarDate")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date hopeStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date hopeEndDate;

    private String fncgWndwOrgnCode;
    private String sortOrdrVal;
    private String sortDirection;
    private String hndrEmnb;
    private String nextKey;
    private Integer pageSize;

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
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

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
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

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
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

    public Date getHopeStartDate() {
        return hopeStartDate;
    }

    public void setHopeStartDate(Date hopeStartDate) {
        this.hopeStartDate = hopeStartDate;
    }

    public Date getHopeEndDate() {
        return hopeEndDate;
    }

    public void setHopeEndDate(Date hopeEndDate) {
        this.hopeEndDate = hopeEndDate;
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

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
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
