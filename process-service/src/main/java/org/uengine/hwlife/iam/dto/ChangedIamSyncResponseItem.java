package org.uengine.hwlife.iam.dto;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 결과.
 */
public class ChangedIamSyncResponseItem {

    private String loanCntcNo;
    private String prcsRsltCntn;

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
