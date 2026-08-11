package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 나의 할일 검색 결과 항목.
 */
public class MyTodoItem {

    private String custId;
    private String loanCntcNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd")
    private Date loanHopeDate;
    private String loanPcesMgmtNo;
    private String fncgBpmTaskTrcgNm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss")
    private Date uworStarDttm;
    private String uworNm;
    private String reptHndrEmnb;
    private String reptHndrFncgOrgnCode;
    private String prcdHndrEmnb;
    private String prcdHndrFncgOrgnCode;
    private String fncgBpmUworSttsCntn;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss")
    private Date starDttm;
    private String befoHndrEmnb;
    private String befoFncgOrgnCode;
    private String hndrEmnb;
    private String hndrNm;
    private String hndrOrgnCode;
    private String scrnUrlAddr;
    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;
    private String dstOptnVal; // 배분 규칙 
    private String ruleAcmpVal; // 할당 규칙
    private String mnorExstYn; // 위임여부
    private String bpmBswrClsfCode;
    private String apvlYn; // 결재여부
    private String imgeScanYn; // 이미지스캔여부
    private String fncgBpmPcesId; // root.defId
        

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

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }

    public Date getUworStarDttm() {
        return uworStarDttm;
    }

    public void setUworStarDttm(Date uworStarDttm) {
        this.uworStarDttm = uworStarDttm;
    }

    public String getUworNm() {
        return uworNm;
    }

    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
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

    public String getPrcdHndrEmnb() {
        return prcdHndrEmnb;
    }

    public void setPrcdHndrEmnb(String prcdHndrEmnb) {
        this.prcdHndrEmnb = prcdHndrEmnb;
    }

    public String getPrcdHndrFncgOrgnCode() {
        return prcdHndrFncgOrgnCode;
    }

    public void setPrcdHndrFncgOrgnCode(String prcdHndrFncgOrgnCode) {
        this.prcdHndrFncgOrgnCode = prcdHndrFncgOrgnCode;
    }

    public String getFncgBpmUworSttsCntn() {
        return fncgBpmUworSttsCntn;
    }

    public void setFncgBpmUworSttsCntn(String fncgBpmUworSttsCntn) {
        this.fncgBpmUworSttsCntn = fncgBpmUworSttsCntn;
    }

    public Date getStarDttm() {
        return starDttm;
    }

    public void setStarDttm(Date starDttm) {
        this.starDttm = starDttm;
    }

    public String getBefoHndrEmnb() {
        return befoHndrEmnb;
    }

    public void setBefoHndrEmnb(String befoHndrEmnb) {
        this.befoHndrEmnb = befoHndrEmnb;
    }

    public String getBefoFncgOrgnCode() {
        return befoFncgOrgnCode;
    }

    public void setBefoFncgOrgnCode(String befoFncgOrgnCode) {
        this.befoFncgOrgnCode = befoFncgOrgnCode;
    }

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getHndrNm() {
        return hndrNm;
    }

    public void setHndrNm(String hndrNm) {
        this.hndrNm = hndrNm;
    }

    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }

    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }

    public String getScrnUrlAddr() {
        return scrnUrlAddr;
    }

    public void setScrnUrlAddr(String scrnUrlAddr) {
        this.scrnUrlAddr = scrnUrlAddr;
    }

    public String getFncgBpmTaskLstId() {
        return fncgBpmTaskLstId;
    }

    public void setFncgBpmTaskLstId(String fncgBpmTaskLstId) {
        this.fncgBpmTaskLstId = fncgBpmTaskLstId;
    }

    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }

    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }

    public String getDstOptnVal() {
        return dstOptnVal;
    }

    public void setDstOptnVal(String dstOptnVal) {
        this.dstOptnVal = dstOptnVal;
    }

    public String getRuleAcmpVal() {
        return ruleAcmpVal;
    }

    public void setRuleAcmpVal(String ruleAcmpVal) {
        this.ruleAcmpVal = ruleAcmpVal;
    }

    public String getMnorExstYn() {
        return mnorExstYn;
    }

    public void setMnorExstYn(String mnorExstYn) {
        this.mnorExstYn = mnorExstYn;
    }

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }

    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
    }

    public String getApvlYn() {
        return apvlYn;
    }

    public void setApvlYn(String apvlYn) {
        this.apvlYn = apvlYn;
    }

    public String getImgeScanYn() {
        return imgeScanYn;
    }

    public void setImgeScanYn(String imgeScanYn) {
        this.imgeScanYn = imgeScanYn;
    }

    public String getFncgBpmPcesId() {
        return fncgBpmPcesId;
    }

    public void setFncgBpmPcesId(String fncgBpmPcesId) {
        this.fncgBpmPcesId = fncgBpmPcesId;
    }
}
