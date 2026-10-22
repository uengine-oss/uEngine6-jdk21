package org.uengine.hwlife.iam.dto;

public class RsaKyprResponse {
    private String rsaPubkVal;
    private String fncgCoreRsaKyprId;
    private String lognPssbYn;

    public String getRsaPubkVal() {
        return rsaPubkVal;
    }
    public String getFncgCoreRsaKyprId() {
        return fncgCoreRsaKyprId;
    }
    public String getLognPssbYn() {
        return lognPssbYn;
    }
    
    public void setRsaPubkVal(String rsaPubkVal) {
        this.rsaPubkVal = rsaPubkVal;
    }
    public void setFncgCoreRsaKyprId(String fncgCoreRsaKyprId) {
        this.fncgCoreRsaKyprId = fncgCoreRsaKyprId;
    }
    public void setLognPssbYn(String lognPssbYn) {
        this.lognPssbYn = lognPssbYn;
    }
}
