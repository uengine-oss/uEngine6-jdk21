package org.uengine.hwlife.search.dto;

import java.util.List;

/**
 * loanPcesMgmtNo 기준 진행 업무 조회 응답
 */
public class WorklistByInstIdResponse {

    private List<WorklistByInstIdResponseItem> bswrList;

    public List<WorklistByInstIdResponseItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<WorklistByInstIdResponseItem> bswrList) {
        this.bswrList = bswrList;
    }

}
