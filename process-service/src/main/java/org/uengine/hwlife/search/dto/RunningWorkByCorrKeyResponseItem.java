package org.uengine.hwlife.search.dto;

public class RunningWorkByCorrKeyResponseItem {
    
    private String loanPcesMgmtNo; // 대출프로세스관리번호 
    private String fncgBpmTaskTrcgNm; // BPM 추적 태그
    private String fncgBpmUworSttsCntn; // 현재 진행중인 단위업무(WORKITME) 상태 
    private String prgsSttsNm; // 인스턴스 상태 
    private String prcsrsltCntn; // 실패 사유( 조회 실패시)

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

    public String getPrcsrsltCntn() {
        return prcsrsltCntn;
    }

    public void setPrcsrsltCntn(String prcsrsltCntn) {
        this.prcsrsltCntn = prcsrsltCntn;
    }
}
