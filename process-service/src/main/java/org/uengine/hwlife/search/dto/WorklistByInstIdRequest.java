package org.uengine.hwlife.search.dto;

/**
 * 인스턴스 기준  업무 목록   요청 — POST /search/worklist-by-inst-id JSON body.
 */
public class WorklistByInstIdRequest {

    private String fncgBpmPcesIntcId;

    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }

    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }
}
