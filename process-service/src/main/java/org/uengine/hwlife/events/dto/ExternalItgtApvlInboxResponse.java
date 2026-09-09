package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 통합승인(ItgtApvl) Event Inbox — ESB 응답 {@code payload} 업무 DTO.
 *
 * <p>{@code /inbox-apvl} 전용.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalItgtApvlInboxResponse {

    private String loanPcesMgmtNo;
    private String evntNm;
    /** 업무 결과코드(LBMXXXXXX) */
    private String prcsRsltCntn;

    public ExternalItgtApvlInboxResponse() {
    }

    public ExternalItgtApvlInboxResponse(String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
        this.evntNm = evntNm;
        this.prcsRsltCntn = prcsRsltCntn;
    }

    /** 처리 성공. {@code prcsRsltCntn} 은 {@code LBM000000}. */
    public static ExternalItgtApvlInboxResponse success(String loanPcesMgmtNo, String evntNm) {
        return new ExternalItgtApvlInboxResponse(loanPcesMgmtNo, evntNm, "LBM000000");
    }

    /**
     * 처리/시스템 실패 상세. {@code prcsRsltCntn} 에 결과코드({@code LBM01XXXX})를 담는다.
     */
    public static ExternalItgtApvlInboxResponse failed(
            String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        return new ExternalItgtApvlInboxResponse(loanPcesMgmtNo, evntNm, prcsRsltCntn);
    }

    public String getLoanPcesMgmtNo() { return loanPcesMgmtNo; }
    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) { this.loanPcesMgmtNo = loanPcesMgmtNo; }

    public String getEvntNm() { return evntNm; }
    public void setEvntNm(String evntNm) { this.evntNm = evntNm; }

    public String getPrcsRsltCntn() { return prcsRsltCntn; }
    public void setPrcsRsltCntn(String prcsRsltCntn) { this.prcsRsltCntn = prcsRsltCntn; }
}
