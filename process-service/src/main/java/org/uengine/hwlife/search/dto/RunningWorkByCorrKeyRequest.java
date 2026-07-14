package org.uengine.hwlife.search.dto;

/**
 * loanPcesMgmtNo 기준 진행 업무 조회 요청 — POST /search/worklist-by-inst-id JSON body.
 */
public class RunningWorkByCorrKeyRequest {

    private String loanPcesMgmtNo;

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }
}
