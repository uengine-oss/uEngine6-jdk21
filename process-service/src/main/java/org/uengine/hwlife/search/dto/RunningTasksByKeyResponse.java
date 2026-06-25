package org.uengine.hwlife.search.dto;

/**
 * corrKey 기준 진행 중 업무 검색 응답 (단건).
 */
public class RunningTasksByKeyResponse {

    private String loanPcesMgmtNo;
    private String trcTag;
    private String status;
    private String rsltMsgeCntn;

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }

    public String getTrcTag() {
        return trcTag;
    }

    public void setTrcTag(String trcTag) {
        this.trcTag = trcTag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRsltMsgeCntn() {
        return rsltMsgeCntn;
    }

    public void setRsltMsgeCntn(String rsltMsgeCntn) {
        this.rsltMsgeCntn = rsltMsgeCntn;
    }
}
