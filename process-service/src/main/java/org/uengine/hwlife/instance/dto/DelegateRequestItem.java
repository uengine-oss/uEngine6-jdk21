package org.uengine.hwlife.instance.dto;

/**
 * 다중 업무 위임 요청 항목 — {@link DelegateRequest#getBswrList()} 요소.
 */
public class DelegateRequestItem {

    private String fncgBpmTasklstId;
    private String fncgBpmPcesIntcId;

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
}
