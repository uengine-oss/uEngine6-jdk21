package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 외부 시스템 이벤트 Inbox — ESB 응답{@code payload} 업무 DTO.
 *
 * <p>ESB 응답도 {@code { "header": {...}, "payload": {...} }} 구조이며,
 * 이 클래스는 {@code payload} 부에만 해당한다.
 * 봉투는 {@link org.uengine.hwlife.esbclient.dto.EsbResponse} /
 * {@link org.uengine.hwlife.esbclient.support.EsbEnvelope} 로 감싼다.</p>
 *
 * <ul>
 *   <li>{@link #loanPcesMgmtNo} — 대출 처리 관리 번호 (= EventInbox.corrKey)</li>
 *   <li>{@link #evntNm} — 이벤트명 (= EventInbox.eventName)</li>
 *   <li>{@link #prcsRsltCodeNm} — 처리 성공 결과</li>
 *   <li>{@link #prcsRsltCntn} — 실패 시 실패 사유</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalEventInboxResponse {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";

    private String prcsRsltCodeNm; /** 처리 성공 결과 */
    private String loanPcesMgmtNo;
    private String evntNm;
    private String prcsRsltCntn; /** 실패 시 실패 사유 */

    public ExternalEventInboxResponse() {
    }

    public ExternalEventInboxResponse(String prcsRsltCodeNm, String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        this.prcsRsltCodeNm = prcsRsltCodeNm;
        this.loanPcesMgmtNo = loanPcesMgmtNo;
        this.evntNm = evntNm;
        this.prcsRsltCntn = prcsRsltCntn;
    }

    /** 처리 성공. {@code prcsRsltCodeNm} 은 {@code SUCCESS} 로 설정된다. */
    public static ExternalEventInboxResponse success(String loanPcesMgmtNo, String evntNm) {
        return new ExternalEventInboxResponse(STATUS_SUCCESS, loanPcesMgmtNo, evntNm, null);
    }

    /** 처리 실패. {@code prcsRsltCntn} 에 실패 사유를 담는다. */
    public static ExternalEventInboxResponse failed(String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        return new ExternalEventInboxResponse(STATUS_FAILED, loanPcesMgmtNo, evntNm, prcsRsltCntn);
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
