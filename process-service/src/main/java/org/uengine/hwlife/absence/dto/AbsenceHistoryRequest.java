package org.uengine.hwlife.absence.dto;

/**
 * 부재 이력 조회 요청 — POST /absences/history JSON body.
 */
public class AbsenceHistoryRequest {

    private String abscEmnb;
    
    private Integer pageNo;

    public String getAbscEmnb() {
        return abscEmnb;
    }

    public void setAbscEmnb(String abscEmnb) {
        this.abscEmnb = abscEmnb;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }
}
