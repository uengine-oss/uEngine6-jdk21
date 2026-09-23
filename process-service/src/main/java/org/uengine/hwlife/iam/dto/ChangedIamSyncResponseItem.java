package org.uengine.hwlife.iam.dto;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 결과.
 */
public class ChangedIamSyncResponseItem {

    private String loanCntnNo;
    private String prcsRsltCntn;

    public String getLoanCntnNo() {
        return loanCntnNo;
    }

    public void setLoanCntnNo(String loanCntnNo) {
        this.loanCntnNo = loanCntnNo;
    }

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
