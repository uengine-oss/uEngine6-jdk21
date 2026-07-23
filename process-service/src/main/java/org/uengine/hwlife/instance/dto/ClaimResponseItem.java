package org.uengine.hwlife.instance.dto;

/**
 * 다중 선점/선점 해제 실패 항목 — {@link ClaimResponse#getFailList()} 요소.
 */
public class ClaimResponseItem {

    private String fncgBpmTaskLstId;// taskId
    private String fncgBpmPcesIntcId; // instanceId
    private String prcsRsltCntn; // 실패 사유.

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

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
