package org.uengine.hwlife.search.dto;

import java.util.List;

public class RunningTasksByKeyRequest {

    private List<RunningTasksByKeyRequestItem> bswrList;

    public List<RunningTasksByKeyRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<RunningTasksByKeyRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
