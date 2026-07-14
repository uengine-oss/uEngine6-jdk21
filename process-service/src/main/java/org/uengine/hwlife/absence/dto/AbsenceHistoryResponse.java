package org.uengine.hwlife.absence.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 부재 이력 조회 응답.
 */
public class AbsenceHistoryResponse {

    private List<AbsenceHistoryItem> abscList = new ArrayList<>();
    private Integer totCont;

    public List<AbsenceHistoryItem> getAbscList() {
        return abscList;
    }

    public void setAbscList(List<AbsenceHistoryItem> abscList) {
        this.abscList = abscList;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
