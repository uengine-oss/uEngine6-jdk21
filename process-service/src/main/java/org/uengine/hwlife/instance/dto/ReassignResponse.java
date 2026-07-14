package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 담당자 변경 응답 — POST /instance/multi-reassign.
 */
public class ReassignResponse {

    private String prcsRsltCodeNm;
    private Integer sucsCont;
    private Integer failCont;
    private List<ReassignResponseItem> failList = new ArrayList<>();

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

    public List<ReassignResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<ReassignResponseItem> failList) {
        this.failList = failList;
    }
}
