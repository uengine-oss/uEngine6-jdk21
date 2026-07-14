package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 선점/선점 해제 요청 — POST /instance/multi-claim JSON body.
 */
public class ClaimRequest {

    private String hndrEmnb;
    private String bswrClsfCode;
    private List<ClaimRequestItem> bswrList;

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getBswrClsfCode() {
        return bswrClsfCode;
    }

    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

    public List<ClaimRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<ClaimRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
