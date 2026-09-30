package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 SKIP 요청 — POST /instance/skip.
 */
public class TaskSkipRequest {

    private String rqsrEmnb; // 요청자 사번 
    private String hndrEmnb; // 처리자 사번 
    private String hndrOrgnCode; // 처리자 기관코드 
    private String hndrAtrtId; //처리자 권한 코드 
    private String fncgBpmTaskLstId;

    public String getRqsrEmnb() {
        return rqsrEmnb;
    }

    public void setRqsrEmnb(String rqsrEmnb) {
        this.rqsrEmnb = rqsrEmnb;
    }

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }

    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }

    public String getHndrAtrtId() {
        return hndrAtrtId;
    }

    public void setHndrAtrtId(String hndrAtrtId) {
        this.hndrAtrtId = hndrAtrtId;
    }

    public String getFncgBpmTaskLstId() {
        return fncgBpmTaskLstId;
    }

    public void setFncgBpmTaskLstId(String fncgBpmTaskLstId) {
        this.fncgBpmTaskLstId = fncgBpmTaskLstId;
    }
}
