package org.uengine.hwlife.absence.dto;

/**
 * 부재 설정/해제 응답 — POST /absences.
 *
 * <p>{@link #prcsRsltCntn} 에 처리결과 코드({@code LBM000000} / {@code LBM03XXXX})를 담는다.</p>
 */
public class AbsenceResponse {

    /** 처리결과 코드 */
    private String prcsRsltCntn;

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }

}
