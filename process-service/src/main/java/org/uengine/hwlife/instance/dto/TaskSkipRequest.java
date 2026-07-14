package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 SKIP 요청 — POST /instance/skip.
 */
public class TaskSkipRequest {

    private String hndrEmnb;
    private String fncgBpmTaskLstId;

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getFncgBpmTaskLstId() {
        return fncgBpmTaskLstId;
    }

    public void setFncgBpmTaskLstId(String fncgBpmTaskLstId) {
        this.fncgBpmTaskLstId = fncgBpmTaskLstId;
    }
}
