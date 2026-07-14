package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 조직 완료 건 검색 응답.
 */
public class OrgCompletedResponse {

    private List<OrgCompletedItem> orgnCpltlist = new ArrayList<>();
    private Integer totCont;

    public List<OrgCompletedItem> getOrgnCpltlist() {
        return orgnCpltlist;
    }

    public void setOrgnCpltlist(List<OrgCompletedItem> orgnCpltlist) {
        this.orgnCpltlist = orgnCpltlist;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
