package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 업무 위임 요청 — POST /instance/multi-delegate JSON body.
 */
public class DelegateRequest {

    private String mnorEmnb;
    private String hndrEmnb;
    private List<DelegateRequestItem> bswrList;

    public String getMnorEmnb() {
        return mnorEmnb;
    }

    public void setMnorEmnb(String mnorEmnb) {
        this.mnorEmnb = mnorEmnb;
    }

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public List<DelegateRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<DelegateRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
