package org.uengine.hwlife.search.dto;

/**
 * loanPcesMgmtNo 기준 진행 업무 조회 응답 (단건).
 */
public class RunningWorkByCorrKeyResponse {

    private String loanPcesMgmtNo;
    private String fncgBpmTaskTrcgNm;
    private String fncgBpmUworSttsCntn;
    private String prgsSttsNm;
    private String prcsRsltCntn;

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }

    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }

    public String getFncgBpmUworSttsCntn() {
        return fncgBpmUworSttsCntn;
    }

    public void setFncgBpmUworSttsCntn(String fncgBpmUworSttsCntn) {
        this.fncgBpmUworSttsCntn = fncgBpmUworSttsCntn;
    }

    public String getPrgsSttsNm() {
        return prgsSttsNm;
    }

    public void setPrgsSttsNm(String prgsSttsNm) {
        this.prgsSttsNm = prgsSttsNm;
    }

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
