package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 반송(이전 단계) 요청 — POST /instance/return.
 */
public class TaskReturnRequest {

    private String rqsrEmnb;
    private String fncgBpmPcesIntcId;
    private String fncgBpmTaskTrcgNm;

    public String getRqsrEmnb() {
        return rqsrEmnb;
    }

    public void setRqsrEmnb(String rqsrEmnb) {
        this.rqsrEmnb = rqsrEmnb;
    }

    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }

    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }
}
