package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 나의 할일 검색 응답.
 */
public class MyTodoResponse {

    private List<MyTodoItem> todoList = new ArrayList<>();
    private String nextKey;
    private Integer totCont;

    public List<MyTodoItem> getTodoList() {
        return todoList;
    }

    public void setTodoList(List<MyTodoItem> todoList) {
        this.todoList = todoList;
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
