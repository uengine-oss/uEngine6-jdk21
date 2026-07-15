package org.uengine.hwlife.esbclient.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ESB 표준전문 공통 헤더 (시스템 공통부 + 요청정보 + 응답정보 + 메시지).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsbCommonHeader {

    // ── 시스템 공통부 ──
    private String trnmSysCode;
    private String ipAddr;
    private String tlgrCretDttm;
    private String rndmNo;
    private Integer hsno;
    private String ctfnTokn;
    private String ogtsTrnnNo;
    private String prsnInfoIncsYn;
    private String itfcId;
    private String rcveSrvcId;
    private String rcveSysCode;
    private String mciNodeNo;
    private String mciSesnId;
    private String serverType;
    private String rspnDvsnCode;
    private String extlDvsnCode;

    // ── 요청정보 ──
    private String emnb;
    private String belnOrgnCode;
    private String custId;
    private String chnlTypeCode;
    private String scrnId;
    private String befoScrnId;
    private String userTmunIdnfVal;
    private String rqsrIp;
    private String rqstDttm;
    private String baseCrny;
    private String baseCnty;
    private String baseLang;
    private String tscsRqstVal;
    private String postfixSysCode;

    // ── 응답정보 / 메시지 ──
    private String tlgrRspnDttm;
    private String prcsRsltDvsnCode;
    private Integer totalCount;
    private String lastPageYn;
    private Integer msgeListCont;
    private List<EsbMessage> msgeList;
    private String msgeStackTrace;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EsbCommonHeader h = new EsbCommonHeader();

        public Builder trnmSysCode(String v) { h.trnmSysCode = v; return this; }
        public Builder ipAddr(String v) { h.ipAddr = v; return this; }
        public Builder tlgrCretDttm(String v) { h.tlgrCretDttm = v; return this; }
        public Builder rndmNo(String v) { h.rndmNo = v; return this; }
        public Builder hsno(Integer v) { h.hsno = v; return this; }
        public Builder ctfnTokn(String v) { h.ctfnTokn = v; return this; }
        public Builder ogtsTrnnNo(String v) { h.ogtsTrnnNo = v; return this; }
        public Builder prsnInfoIncsYn(String v) { h.prsnInfoIncsYn = v; return this; }
        public Builder itfcId(String v) { h.itfcId = v; return this; }
        public Builder rcveSrvcId(String v) { h.rcveSrvcId = v; return this; }
        public Builder rcveSysCode(String v) { h.rcveSysCode = v; return this; }
        public Builder mciNodeNo(String v) { h.mciNodeNo = v; return this; }
        public Builder mciSesnId(String v) { h.mciSesnId = v; return this; }
        public Builder serverType(String v) { h.serverType = v; return this; }
        public Builder rspnDvsnCode(String v) { h.rspnDvsnCode = v; return this; }
        public Builder extlDvsnCode(String v) { h.extlDvsnCode = v; return this; }
        public Builder emnb(String v) { h.emnb = v; return this; }
        public Builder belnOrgnCode(String v) { h.belnOrgnCode = v; return this; }
        public Builder custId(String v) { h.custId = v; return this; }
        public Builder chnlTypeCode(String v) { h.chnlTypeCode = v; return this; }
        public Builder scrnId(String v) { h.scrnId = v; return this; }
        public Builder befoScrnId(String v) { h.befoScrnId = v; return this; }
        public Builder userTmunIdnfVal(String v) { h.userTmunIdnfVal = v; return this; }
        public Builder rqsrIp(String v) { h.rqsrIp = v; return this; }
        public Builder rqstDttm(String v) { h.rqstDttm = v; return this; }
        public Builder baseCrny(String v) { h.baseCrny = v; return this; }
        public Builder baseCnty(String v) { h.baseCnty = v; return this; }
        public Builder baseLang(String v) { h.baseLang = v; return this; }
        public Builder tscsRqstVal(String v) { h.tscsRqstVal = v; return this; }
        public Builder postfixSysCode(String v) { h.postfixSysCode = v; return this; }

        public EsbCommonHeader build() {
            return h;
        }
    }

    public String getTrnmSysCode() { return trnmSysCode; }
    public void setTrnmSysCode(String trnmSysCode) { this.trnmSysCode = trnmSysCode; }
    public String getIpAddr() { return ipAddr; }
    public void setIpAddr(String ipAddr) { this.ipAddr = ipAddr; }
    public String getTlgrCretDttm() { return tlgrCretDttm; }
    public void setTlgrCretDttm(String tlgrCretDttm) { this.tlgrCretDttm = tlgrCretDttm; }
    public String getRndmNo() { return rndmNo; }
    public void setRndmNo(String rndmNo) { this.rndmNo = rndmNo; }
    public Integer getHsno() { return hsno; }
    public void setHsno(Integer hsno) { this.hsno = hsno; }
    public String getCtfnTokn() { return ctfnTokn; }
    public void setCtfnTokn(String ctfnTokn) { this.ctfnTokn = ctfnTokn; }
    public String getOgtsTrnnNo() { return ogtsTrnnNo; }
    public void setOgtsTrnnNo(String ogtsTrnnNo) { this.ogtsTrnnNo = ogtsTrnnNo; }
    public String getPrsnInfoIncsYn() { return prsnInfoIncsYn; }
    public void setPrsnInfoIncsYn(String prsnInfoIncsYn) { this.prsnInfoIncsYn = prsnInfoIncsYn; }
    public String getItfcId() { return itfcId; }
    public void setItfcId(String itfcId) { this.itfcId = itfcId; }
    public String getRcveSrvcId() { return rcveSrvcId; }
    public void setRcveSrvcId(String rcveSrvcId) { this.rcveSrvcId = rcveSrvcId; }
    public String getRcveSysCode() { return rcveSysCode; }
    public void setRcveSysCode(String rcveSysCode) { this.rcveSysCode = rcveSysCode; }
    public String getMciNodeNo() { return mciNodeNo; }
    public void setMciNodeNo(String mciNodeNo) { this.mciNodeNo = mciNodeNo; }
    public String getMciSesnId() { return mciSesnId; }
    public void setMciSesnId(String mciSesnId) { this.mciSesnId = mciSesnId; }
    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }
    public String getRspnDvsnCode() { return rspnDvsnCode; }
    public void setRspnDvsnCode(String rspnDvsnCode) { this.rspnDvsnCode = rspnDvsnCode; }
    public String getExtlDvsnCode() { return extlDvsnCode; }
    public void setExtlDvsnCode(String extlDvsnCode) { this.extlDvsnCode = extlDvsnCode; }
    public String getEmnb() { return emnb; }
    public void setEmnb(String emnb) { this.emnb = emnb; }
    public String getBelnOrgnCode() { return belnOrgnCode; }
    public void setBelnOrgnCode(String belnOrgnCode) { this.belnOrgnCode = belnOrgnCode; }
    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }
    public String getChnlTypeCode() { return chnlTypeCode; }
    public void setChnlTypeCode(String chnlTypeCode) { this.chnlTypeCode = chnlTypeCode; }
    public String getScrnId() { return scrnId; }
    public void setScrnId(String scrnId) { this.scrnId = scrnId; }
    public String getBefoScrnId() { return befoScrnId; }
    public void setBefoScrnId(String befoScrnId) { this.befoScrnId = befoScrnId; }
    public String getUserTmunIdnfVal() { return userTmunIdnfVal; }
    public void setUserTmunIdnfVal(String userTmunIdnfVal) { this.userTmunIdnfVal = userTmunIdnfVal; }
    public String getRqsrIp() { return rqsrIp; }
    public void setRqsrIp(String rqsrIp) { this.rqsrIp = rqsrIp; }
    public String getRqstDttm() { return rqstDttm; }
    public void setRqstDttm(String rqstDttm) { this.rqstDttm = rqstDttm; }
    public String getBaseCrny() { return baseCrny; }
    public void setBaseCrny(String baseCrny) { this.baseCrny = baseCrny; }
    public String getBaseCnty() { return baseCnty; }
    public void setBaseCnty(String baseCnty) { this.baseCnty = baseCnty; }
    public String getBaseLang() { return baseLang; }
    public void setBaseLang(String baseLang) { this.baseLang = baseLang; }
    public String getTscsRqstVal() { return tscsRqstVal; }
    public void setTscsRqstVal(String tscsRqstVal) { this.tscsRqstVal = tscsRqstVal; }
    public String getPostfixSysCode() { return postfixSysCode; }
    public void setPostfixSysCode(String postfixSysCode) { this.postfixSysCode = postfixSysCode; }
    public String getTlgrRspnDttm() { return tlgrRspnDttm; }
    public void setTlgrRspnDttm(String tlgrRspnDttm) { this.tlgrRspnDttm = tlgrRspnDttm; }
    public String getPrcsRsltDvsnCode() { return prcsRsltDvsnCode; }
    public void setPrcsRsltDvsnCode(String prcsRsltDvsnCode) { this.prcsRsltDvsnCode = prcsRsltDvsnCode; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public String getLastPageYn() { return lastPageYn; }
    public void setLastPageYn(String lastPageYn) { this.lastPageYn = lastPageYn; }
    public Integer getMsgeListCont() { return msgeListCont; }
    public void setMsgeListCont(Integer msgeListCont) { this.msgeListCont = msgeListCont; }
    public List<EsbMessage> getMsgeList() { return msgeList; }
    public void setMsgeList(List<EsbMessage> msgeList) { this.msgeList = msgeList; }
    public String getMsgeStackTrace() { return msgeStackTrace; }
    public void setMsgeStackTrace(String msgeStackTrace) { this.msgeStackTrace = msgeStackTrace; }
}
