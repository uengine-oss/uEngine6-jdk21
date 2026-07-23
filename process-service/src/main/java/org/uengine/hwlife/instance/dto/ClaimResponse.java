package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 선점/선점 해제 응답 — POST /instance/multi-claim.
 */
public class ClaimResponse {

    private String prcsRsltCodeNm; // 처리결과: SUCCESS, FAILED
    private Integer sucsCont; // 성공 건수 
    private Integer failCont; // 실패 건수 
    private List<ClaimResponseItem> failList = new ArrayList<>(); // 실패 목록

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

    public List<ClaimResponseItem> getFailList() {
        return failList;
    }

    public void setFailList(List<ClaimResponseItem> failList) {
        this.failList = failList;
    }
}
