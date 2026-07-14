package org.uengine.hwlife.instance.dto;

/**
 * 다중 선점/선점 해제 요청 항목 — {@link ClaimRequest#getBswrList()} 요소.
 */
public class ClaimRequestItem {

    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;

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
}
