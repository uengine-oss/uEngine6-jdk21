package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 일괄 배정 요청 — PUT /instance/bulk-assign JSON body.
 */
public class BulkAssignRequest {

    private List<BulkAssignRequestItem> bswrList;

    public List<BulkAssignRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<BulkAssignRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
