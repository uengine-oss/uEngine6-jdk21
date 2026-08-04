package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 선점/선점 해제 요청 — POST /instance/multi-claim JSON body.
 *
 * <p>처리자 사번·소속기관은 body 가 아니라 ESB header.emnb / header.belnOrgnCode 를 사용한다.</p>
 */
public class ClaimRequest {

    private String dvsnVal; // 선점: 0 / 선점 해제: 1
    private List<ClaimRequestItem> bswrList; // 업무 목록

    public String getDvsnVal() {
        return dvsnVal;
    }

    public void setDvsnVal(String dvsnVal) {
        this.dvsnVal = dvsnVal;
    }

    public List<ClaimRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<ClaimRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
