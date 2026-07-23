package org.uengine.hwlife.search.dto;

/**
 * 인스턴스 기준  업무 목록   요청 — POST /search/worklist-by-inst-id JSON body.
 */
public class WorklistByInstIdRequest {

    private String loanPcesMgmtNo;

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }
}
