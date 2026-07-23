package org.uengine.hwlife.search.dto;

import java.util.List;

public class RunningWorkByCorrKeyRequest {

    private List<RunningWorkByCorrKeyRequestItem> bswrList;
    
    public List<RunningWorkByCorrKeyRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<RunningWorkByCorrKeyRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
