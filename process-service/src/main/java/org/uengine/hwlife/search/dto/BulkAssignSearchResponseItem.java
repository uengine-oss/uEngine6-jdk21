package org.uengine.hwlife.search.dto;

/**
 * 일괄 배정 대상 검색 결과 항목 — {@link BulkAssignSearchResponse#getBswrList()} 요소.
 */
public class BulkAssignSearchResponseItem {

    private String bswrClsfCode;
    private String fncgBswrDvsnCode;
    private String fncgBpmTaskLstId;
    private String fncgBpmPcesIntcId;
    private String uworNm;
    private String loanPcesNm;

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public String getFncgBswrDvsnCode() {
        return fncgBswrDvsnCode;
    }

    public void setFncgBswrDvsnCode(String fncgBswrDvsnCode) {
        this.fncgBswrDvsnCode = fncgBswrDvsnCode;
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

    public String getLoanPcesNm() {
        return loanPcesNm;
    }

    public void setLoanPcesNm(String loanPcesNm) {
        this.loanPcesNm = loanPcesNm;
    }
}
