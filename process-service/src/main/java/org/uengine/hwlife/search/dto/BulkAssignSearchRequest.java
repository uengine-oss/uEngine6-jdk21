package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 일괄 배정 대상 검색 요청 — POST /search/bulk-assign JSON body.
 */
public class BulkAssignSearchRequest {

    private String bpmBswrClsfCode;
    private String custId;
    private String loanCntcNo;
    private String loanPcesMgmtNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date starDate;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date endDate;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date hopeStarDate;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date hopeEndDate;

    /** 요청기관 필터 — {@code bpm_procinst.init_group_cd} 와 일치. */
    private String fncgWndwOrgnCode;
    /** 업무구분값 — 해당 인스턴스의 root instance {@code defId}. */
    private String bswrDvsnVal;
    private String uworNm;

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }
    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
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
    public Date getStarDate() {
        return starDate;
    }
    public void setStarDate(Date starDate) {
        this.starDate = starDate;
    }
    public Date getEndDate() {
        return endDate;
    }
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
    public Date getHopeStarDate() {
        return hopeStarDate;
    }
    public void setHopeStarDate(Date hopeStarDate) {
        this.hopeStarDate = hopeStarDate;
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
    public String getBswrDvsnVal() {
        return bswrDvsnVal;
    }
    public void setBswrDvsnVal(String bswrDvsnVal) {
        this.bswrDvsnVal = bswrDvsnVal;
    }
    public String getUworNm() {
        return uworNm;
    }
    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
    }

}
