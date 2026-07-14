package org.uengine.hwlife.instance.dto;

/**
 * 일괄 배정 요청 항목 — {@link BulkAssignRequest#getBswrList()} 요소.
 */
public class BulkAssignRequestItem {

    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;
    private String hndrEmnb;

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

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }
}
