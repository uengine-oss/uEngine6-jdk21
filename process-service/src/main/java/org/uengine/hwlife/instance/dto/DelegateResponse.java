package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 업무 위임 응답 — POST /instance/multi-delegate.
 *
 * <p>ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.</p>
 */
public class DelegateResponse {

    private Integer sucsCont;
    private Integer failCont;
    private List<DelegateResponseItem> failList = new ArrayList<>();

    public Integer getSucsCont() {
        return sucsCont;
    }

    public void setSucsCont(Integer sucsCont) {
        this.sucsCont = sucsCont;
    }

    public Integer getFailCont() {
        return failCont;
    }

    public void setFailCont(Integer failCont) {
        this.failCont = failCont;
    }

    public List<DelegateResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<DelegateResponseItem> failList) {
        this.failList = failList;
    }
}
