package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 나의 진행 검색 응답.
 */
public class MyProgressResponse {

    private List<MyProgressItem> todoPrgsList = new ArrayList<>();
    private long totCont;

    public List<MyProgressItem> getTodoPrgsList() {
        return todoPrgsList;
    }

    public void setTodoPrgsList(List<MyProgressItem> todoPrgsList) {
        this.todoPrgsList = todoPrgsList;
    }

    public long getTotCont() {
        return totCont;
    }

    public void setTotCont(long totCont) {
        this.totCont = totCont;
    }
}
