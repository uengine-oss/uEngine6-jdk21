package org.uengine.hwlife.search.dto;

import java.util.List;

public class RunningTasksByKeyResponse {

    private List<RunningTasksByKeyResponseItem> bswrList;

    public List<RunningTasksByKeyResponseItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<RunningTasksByKeyResponseItem> bswrList) {
        this.bswrList = bswrList;
    }
}
