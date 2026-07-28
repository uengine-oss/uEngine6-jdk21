package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 나의 진행 검색 응답.
 */
public class MyProgressResponse {

    private List<MyProgressItem> todoPrgsList = new ArrayList<>();
    private String nextKey;
    private Integer totCont;

    public List<MyProgressItem> getTodoPrgsList() {
        return todoPrgsList;
    }

    public void setTodoPrgsList(List<MyProgressItem> todoPrgsList) {
        this.todoPrgsList = todoPrgsList;
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
