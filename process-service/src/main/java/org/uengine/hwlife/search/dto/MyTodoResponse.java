package org.uengine.hwlife.search.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 나의 할일 검색 응답.
 */
public class MyTodoResponse {

    private List<MyTodoItem> todolist = new ArrayList<>();
    private Integer totCont;

    public List<MyTodoItem> getTodolist() {
        return todolist;
    }

    public void setTodolist(List<MyTodoItem> todolist) {
        this.todolist = todolist;
    }

    public Integer getTotCont() {
        return totCont;
    }

    public void setTotCont(Integer totCont) {
        this.totCont = totCont;
    }
}
