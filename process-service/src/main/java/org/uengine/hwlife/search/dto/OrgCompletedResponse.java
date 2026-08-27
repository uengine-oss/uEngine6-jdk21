package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 조직 완료 건 검색 응답.
 */
public class OrgCompletedResponse {

    private List<OrgCompletedItem> orgnCpltList = new ArrayList<>();
    private String nextKey;
    private Integer totCont;

    public List<OrgCompletedItem> getOrgnCpltList() {
        return orgnCpltList;
    }

    public void setOrgnCpltList(List<OrgCompletedItem> orgnCpltList) {
        this.orgnCpltList = orgnCpltList;
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
