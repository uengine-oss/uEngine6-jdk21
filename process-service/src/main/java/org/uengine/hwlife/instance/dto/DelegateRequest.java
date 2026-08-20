package org.uengine.hwlife.instance.dto;

import java.util.List;

/**
 * 다중 업무 위임 요청 — POST /instance/multi-delegate JSON body.
 *
 * <p>위임자 사번은 body 가 아니라 ESB header.emnb 를 사용한다.
 * 처리자 사번은 {@code hndrEmnb} 이며, 기관코드는 IAM 조회로 확인한다.</p>
 */
public class DelegateRequest {

    private String hndrEmnb;
    private String hndrOrgnCode;
    private List<DelegateRequestItem> bswrList;

    public String getHndrEmnb() {
        return hndrEmnb;
    }

    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }

    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }

    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }

    public List<DelegateRequestItem> getBswrList() {
        return bswrList;
    }

    public void setBswrList(List<DelegateRequestItem> bswrList) {
        this.bswrList = bswrList;
    }
}
