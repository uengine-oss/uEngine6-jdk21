package org.uengine.hwlife.search.dto;

/**
 * 일괄 배정 대상 검색 결과 항목 — {@link BulkAssignSearchResponse#getBswrList()} 요소.
 */
public class BulkAssignSearchResponseItem {

    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;
    private String bpmBswrClsfCode; // 업무분류코드 (inst.bswrClsfCode)
    private String uworNm; // 태스크 명
    private String bswrDvsnVal; // root_inst_id.defId (현 최상 업무 정의 아이디)
    

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }

    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
    }

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

    public String getUworNm() {
        return uworNm;
    }

    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
    }

    public String getBswrDvsnVal() {
        return bswrDvsnVal;
    }

    public void setBswrDvsnVal(String bswrDvsnVal) {
        this.bswrDvsnVal = bswrDvsnVal;
    }
}
