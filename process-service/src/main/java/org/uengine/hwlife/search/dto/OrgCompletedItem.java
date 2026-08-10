package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
/**
 * 조직 완료 건 검색 결과 항목 — {@link OrgCompletedResponse#getOrgnCpltlist()} 요소.
 */
public class OrgCompletedItem {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmssSSS")
    private Date starDttm;
    private String fncgBswrDvsnCode;
    private String loanCntcNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String custId;
    private String fncgMneyUsagClsfCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd")
    private Date loanHopeDate;
    private String loanPcesMgmtNo;
    private String reptHndrEmnb;
    private String reptHndrFncgOrgnCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmssSSS")
    private Date endDttm;
    private String fncgBpmtaskLstId;
    private String fncgBpmPcesIntcId;

    public Date getStarDttm() {
        return starDttm;
    }

    public void setStarDttm(Date starDttm) {
        this.starDttm = starDttm;
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

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
    }

    public String getFncgMneyUsagClsfCode() {
        return fncgMneyUsagClsfCode;
    }

    public void setFncgMneyUsagClsfCode(String fncgMneyUsagClsfCode) {
        this.fncgMneyUsagClsfCode = fncgMneyUsagClsfCode;
    }

    public Date getLoanHopeDate() {
        return loanHopeDate;
    }

    public void setLoanHopeDate(Date loanHopeDate) {
        this.loanHopeDate = loanHopeDate;
    }

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }

    public String getReptHndrEmnb() {
        return reptHndrEmnb;
    }

    public void setReptHndrEmnb(String reptHndrEmnb) {
        this.reptHndrEmnb = reptHndrEmnb;
    }

    public String getReptHndrFncgOrgnCode() {
        return reptHndrFncgOrgnCode;
    }

    public void setReptHndrFncgOrgnCode(String reptHndrFncgOrgnCode) {
        this.reptHndrFncgOrgnCode = reptHndrFncgOrgnCode;
    }

    public Date getEndDttm() {
        return endDttm;
    }

    public void setEndDttm(Date endDttm) {
        this.endDttm = endDttm;
    }

    public String getFncgBpmtaskLstId() {
        return fncgBpmtaskLstId;
    }

    public void setFncgBpmtaskLstId(String fncgBpmtaskLstId) {
        this.fncgBpmtaskLstId = fncgBpmtaskLstId;
    }

    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }

    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }
}
