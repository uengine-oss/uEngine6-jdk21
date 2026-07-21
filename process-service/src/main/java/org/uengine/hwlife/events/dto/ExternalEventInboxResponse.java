package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 외부 시스템 이벤트 Inbox 응답 DTO.
 *
 * <p>요청 payload 에서 꺼낸 업무 식별자와 처리 결과를 되돌려준다.
 * <ul>
 *   <li>{@link #loanPcesMgmtNo} — 대출 처리 관리 번호 (= EventInbox.corrKey)</li>
 *   <li>{@link #evntNm} — 이벤트명 (= EventInbox.eventName)</li>
 *   <li>{@link #prcsRsltCodeNm} — 처리 성공 결과</li>
 *   <li>{@link #prcsRsltCntn} — 실패 시 실패 사유</li>
 * </ul></p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalEventInboxResponse {

    private String loanPcesMgmtNo;

    private String evntNm;

    /** 처리 성공 결과 */
    private String prcsRsltCodeNm;

    /** 실패 시 실패 사유 */
    private String prcsRsltCntn;

    public ExternalEventInboxResponse() {
    }

    public ExternalEventInboxResponse(String loanPcesMgmtNo, String evntNm) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
        this.evntNm = evntNm;
    }

    /** 처리 성공. {@code prcsRsltCodeNm} 은 {@code SUCCESS} 로 설정된다. */
    public static ExternalEventInboxResponse success(String loanPcesMgmtNo, String evntNm) {
        ExternalEventInboxResponse r = new ExternalEventInboxResponse(loanPcesMgmtNo, evntNm);
        r.prcsRsltCodeNm = "SUCCESS";
        return r;
    }

    /** 처리 실패. {@code prcsRsltCntn} 에 실패 사유를 담는다. */
    public static ExternalEventInboxResponse failed(String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        ExternalEventInboxResponse r = new ExternalEventInboxResponse(loanPcesMgmtNo, evntNm);
        r.prcsRsltCodeNm = "FAILED";
        r.prcsRsltCntn = prcsRsltCntn;
        return r;
    }

    public String getLoanPcesMgmtNo() { return loanPcesMgmtNo; }
    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) { this.loanPcesMgmtNo = loanPcesMgmtNo; }

    public String getEvntNm() { return evntNm; }
    public void setEvntNm(String evntNm) { this.evntNm = evntNm; }

    public String getPrcsRsltCodeNm() { return prcsRsltCodeNm; }
    public void setPrcsRsltCodeNm(String prcsRsltCodeNm) { this.prcsRsltCodeNm = prcsRsltCodeNm; }

    public String getPrcsRsltCntn() { return prcsRsltCntn; }
    public void setPrcsRsltCntn(String prcsRsltCntn) { this.prcsRsltCntn = prcsRsltCntn; }
}
