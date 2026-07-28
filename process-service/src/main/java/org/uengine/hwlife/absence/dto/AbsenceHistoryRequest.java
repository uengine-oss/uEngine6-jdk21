package org.uengine.hwlife.absence.dto;

/**
 * 부재 이력 조회 요청 — POST /absences/history JSON body.
 */
public class AbsenceHistoryRequest {

    private String abscEmnb;
    
    private String nextKey;
    private Integer pageSize;

    public String getAbscEmnb() {
        return abscEmnb;
    }

    public void setAbscEmnb(String abscEmnb) {
        this.abscEmnb = abscEmnb;
    }

    public String getNextKey() {
        return nextKey;
    }

    public void setNextKey(String nextKey) {
        this.nextKey = nextKey;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
