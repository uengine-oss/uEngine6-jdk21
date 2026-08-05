package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 조직 진행 건 검색 응답.
 */
public class OrgRunningResponse {

    private List<OrgRunningItem> orgnPrgsList = new ArrayList<>();
    private String nextKey;
    private Integer totCont;

    public List<OrgRunningItem> getOrgnPrgsList() {
        return orgnPrgsList;
    }

    public void setOrgnPrgsList(List<OrgRunningItem> orgnPrgsList) {
        this.orgnPrgsList = orgnPrgsList;
    }

    public String getNextKey() {
        return nextKey;
    }

    public void setNextKey(String nextKey) {
        this.nextKey = nextKey;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
