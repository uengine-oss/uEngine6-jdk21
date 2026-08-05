package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 담당자 변경 응답 — POST /instance/multi-reassign.
 *
 * <p>ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.</p>
 */
public class ReassignResponse {

    private Integer sucsCont;
    private Integer failCont;
    private List<ReassignResponseItem> failList = new ArrayList<>();

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

    public List<ReassignResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<ReassignResponseItem> failList) {
        this.failList = failList;
    }
}
