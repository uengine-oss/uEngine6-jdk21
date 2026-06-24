package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 나의 할일 검색 응답.
 */
public class MyTodoResponse {

    private List<MyTodoItem> todoList = new ArrayList<>();
    private long totCont;

    public List<MyTodoItem> getTodoList() {
        return todoList;
    }

    public void setTodoList(List<MyTodoItem> todoList) {
        this.todoList = todoList;
    }

    public long getTotCont() {
        return totCont;
    }

    public void setTotCont(long totCont) {
        this.totCont = totCont;
    }
}
