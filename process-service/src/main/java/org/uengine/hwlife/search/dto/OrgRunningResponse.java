package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 조직 진행 건 검색 응답.
 */
public class OrgRunningResponse {

    private List<OrgRunningItem> orgnPrgslist = new ArrayList<>();
    private Integer totCont;

    public List<OrgRunningItem> getOrgnPrgslist() {
        return orgnPrgslist;
    }

    public void setOrgnPrgslist(List<OrgRunningItem> orgnPrgslist) {
        this.orgnPrgslist = orgnPrgslist;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
