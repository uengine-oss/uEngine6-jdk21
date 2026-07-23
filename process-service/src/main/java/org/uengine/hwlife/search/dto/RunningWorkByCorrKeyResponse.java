package org.uengine.hwlife.search.dto;

import java.util.List;

public class RunningWorkByCorrKeyResponse {

    private List<RunningWorkByCorrKeyResponseItem> bswrList;

    public List<RunningWorkByCorrKeyResponseItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<RunningWorkByCorrKeyResponseItem> bswrList) {
        this.bswrList = bswrList;
    }
}
