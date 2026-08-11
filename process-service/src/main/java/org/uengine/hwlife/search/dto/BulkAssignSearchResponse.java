package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 일괄 배정 대상 검색 응답.
 */
public class BulkAssignSearchResponse {

    private List<BulkAssignSearchResponseItem> bswrList = new ArrayList<>();
    private Integer totCont;

    public List<BulkAssignSearchResponseItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<BulkAssignSearchResponseItem> bswrList) {
        this.bswrList = bswrList;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
