package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 선점/선점 해제 요청 — POST /instance/multi-claim JSON body.
 */
public class ClaimRequest {

    private String hndrEmnb; // 처리자 사원번호 
    private String bswrClsfCode; // 업무 분류코드: 선점: 0/선점 해제: 1 
    private List<ClaimRequestItem> bswrList; // 업무 목록

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
