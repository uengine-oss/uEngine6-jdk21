package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 다중 선점/선점 해제 응답 — POST /instance/multi-claim.
 *
 * <p>전부 성공 시 {@link #STATUS_SUCCESS}({@code LBM000000}).
 * 하나라도 실패하면 {@link #STATUS_FAILED}, 사유 코드는 {@link #failList} 각 항목
 * {@code prcsRsltCntn}({@code LBM05XXXX}).</p>
 */
public class ClaimResponse {

    /** 전부 성공 */
    public static final String STATUS_SUCCESS = "LBM000000";
    /** 하나 이상 실패 */
    public static final String STATUS_FAILED = "FAILED";

    private String prcsRsltCodeNm; // 처리결과: LBM000000 / FAILED
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
