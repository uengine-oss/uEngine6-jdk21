package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 일괄 배정 응답 — PUT /instance/bulk-assign.
 *
 * <p>ESB header {@code prcsRsltDvsnCode} 는 성공 {@code 0} / 시스템실패 {@code 1}.</p>
 */
public class BulkAssignResponse {

    private Integer sucsCont; // 성공건수
    private Integer failCont; // 실패 건수
    private List<BulkAssignResponseItem> failList = new ArrayList<>(); // 실패 목록

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

    public List<BulkAssignResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<BulkAssignResponseItem> failList) {
        this.failList = failList;
    }
}
