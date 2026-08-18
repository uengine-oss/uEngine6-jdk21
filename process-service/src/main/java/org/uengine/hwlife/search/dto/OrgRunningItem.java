package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.uengine.hwlife.esbclient.dto.EsbCodes;

/**
 * 조직 진행 건 검색 결과 항목 — {@link OrgRunningResponse#getOrgnPrgsList()} 요소.
 */
public class OrgRunningItem {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC)
    private Date starDttm;
    private String loanCntcNo;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String custId;
    private String fncgMneyUsagClsfCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DATE)
    private Date loanHopeDate;
    private String loanPcesMgmtNo;
    private String reptHndrEmnb;
    private String reptHndrFncgOrgnCode;
    private String hndrEmnb;
    private String hndrOrgnCode;
    private String uworNm;
    private String fncgBpmTaskTrcgNm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC)
    private Date uworStarDttm;
    private String bpmBswrClsfCode;
    private String fncgBpmtaskLstId;
    private String fncgBpmPcesIntcId;
    private String bswrDvsnVal; // root.defId (메인/서브)
    private String fncgBpmPcesId; // worklist.defId

    public Date getStarDttm() {
        return starDttm;
    }

    public void setStarDttm(Date starDttm) {
        this.starDttm = starDttm;
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

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }

    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }

    public String getUworNm() {
        return uworNm;
    }

    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
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

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }

    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
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

    public String getBswrDvsnVal() {
        return bswrDvsnVal;
    }

    public void setBswrDvsnVal(String bswrDvsnVal) {
        this.bswrDvsnVal = bswrDvsnVal;
    }

    public String getFncgBpmPcesId() {
        return fncgBpmPcesId;
    }

    public void setFncgBpmPcesId(String fncgBpmPcesId) {
        this.fncgBpmPcesId = fncgBpmPcesId;
    }
}
