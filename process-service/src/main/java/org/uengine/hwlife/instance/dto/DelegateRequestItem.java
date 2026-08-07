package org.uengine.hwlife.instance.dto;

/**
 * 다중 업무 위임 요청 항목 — {@link DelegateRequest#getBswrList()} 요소.
 */
public class DelegateRequestItem {

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
