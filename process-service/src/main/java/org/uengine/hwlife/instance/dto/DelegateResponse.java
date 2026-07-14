package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 업무 위임 응답 — POST /instance/multi-delegate.
 */
public class DelegateResponse {

    private String prcsRsltCodeNm;
    private Integer sucsCont;
    private Integer failCont;
    private List<DelegateResponseItem> failList = new ArrayList<>();

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

    public List<DelegateResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<DelegateResponseItem> failList) {
        this.failList = failList;
    }
}
