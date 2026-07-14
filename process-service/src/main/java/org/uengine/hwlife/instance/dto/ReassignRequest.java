package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 담당자 변경 요청 — POST /instance/multi-reassign JSON body.
 */
public class ReassignRequest {

    private List<ReassignRequestItem> bswrList;

    public List<ReassignRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<ReassignRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
