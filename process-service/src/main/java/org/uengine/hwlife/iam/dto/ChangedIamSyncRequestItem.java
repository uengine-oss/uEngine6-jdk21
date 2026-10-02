package org.uengine.hwlife.iam.dto;

/**
 * IAM 변경(기관 통폐합) 건 — 대출계약번호 기준 변경 전/후 기관코드.
 */
public class ChangedIamSyncRequestItem {

    private String loanCntcNo;
    private String chngBefrFncgWndwOrgnCode;
    private String chngAfeqFncgWndwOrgnCode;

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }

    public String getChngBefrFncgWndwOrgnCode() {
        return chngBefrFncgWndwOrgnCode;
    }

    public void setChngBefrFncgWndwOrgnCode(String chngBefrFncgWndwOrgnCode) {
        this.chngBefrFncgWndwOrgnCode = chngBefrFncgWndwOrgnCode;
    }

    public String getChngAfeqFncgWndwOrgnCode() {
        return chngAfeqFncgWndwOrgnCode;
    }

    public void setChngAfeqFncgWndwOrgnCode(String chngAfeqFncgWndwOrgnCode) {
        this.chngAfeqFncgWndwOrgnCode = chngAfeqFncgWndwOrgnCode;
    }
}
