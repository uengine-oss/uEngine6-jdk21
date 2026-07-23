package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 일괄 배정 응답 — PUT /instance/bulk-assign.
 */
public class BulkAssignResponse {

    private String prcsRsltCodeNm; // 처리결과:  SUCCESS/ FAILED
    private Integer sucsCont; // 성공건수 
    private Integer failCont; // 실패 건수
    private List<BulkAssignResponseItem> failList = new ArrayList<>(); // 실패 목록

    public String getPrcsRsltCodeNm() {
        return prcsRsltCodeNm;
    }

    public void setPrcsRsltCodeNm(String prcsRsltCodeNm) {
        this.prcsRsltCodeNm = prcsRsltCodeNm;
    }

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
