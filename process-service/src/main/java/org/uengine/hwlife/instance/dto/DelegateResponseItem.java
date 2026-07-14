package org.uengine.hwlife.instance.dto;

/**
 * 다중 업무 위임 실패 항목 — {@link DelegateResponse#getFailList()} 요소.
 */
public class DelegateResponseItem {

    private String fncgBpmTasklstId;
    private String fncgBpmPcesIntcId;
    private String prcsRsltCntn;

    public String getFncgBpmTasklstId() {
        return fncgBpmTasklstId;
    }

    public void setFncgBpmTasklstId(String fncgBpmTasklstId) {
        this.fncgBpmTasklstId = fncgBpmTasklstId;
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
