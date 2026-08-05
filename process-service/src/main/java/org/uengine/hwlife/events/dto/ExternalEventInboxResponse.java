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
 *   <li>{@link #prcsRsltCntn} — 업무 결과코드 ({@code LBM000000} / {@code LBM01XXXX})</li>
 * </ul>
 *
 * <p>성공/실패 구분은 payload 가 아니라 ESB header {@code prcsRsltDvsnCode} 가 담당한다:
 * <ul>
 *   <li>성공 — {@code 0} (업무 상세는 {@link #prcsRsltCntn})</li>
 *   <li>실패 — {@code 1} (시스템)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalEventInboxResponse {

    private String loanPcesMgmtNo;
    private String evntNm;
    /** 업무 결과코드(LBMXXXXXX) */
    private String prcsRsltCntn;

    public ExternalEventInboxResponse() {
    }

    public ExternalEventInboxResponse(String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
        this.evntNm = evntNm;
        this.prcsRsltCntn = prcsRsltCntn;
    }

    /** 처리 성공. {@code prcsRsltCntn} 은 {@code LBM000000}. */
    public static ExternalEventInboxResponse success(String loanPcesMgmtNo, String evntNm) {
        return new ExternalEventInboxResponse(loanPcesMgmtNo, evntNm, "LBM000000");
    }

    /**
     * 처리/시스템 실패 상세. {@code prcsRsltCntn} 에 결과코드({@code LBM01XXXX})를 담는다.
     * header 성공/실패 구분은 {@link org.uengine.hwlife.esbclient.support.EsbEnvelope} 가 담당한다.
     */
    public static ExternalEventInboxResponse failed(
            String loanPcesMgmtNo, String evntNm, String prcsRsltCntn) {
        return new ExternalEventInboxResponse(loanPcesMgmtNo, evntNm, prcsRsltCntn);
    }

    public String getLoanPcesMgmtNo() {
        return loanPcesMgmtNo;
    }

    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) {
        this.loanPcesMgmtNo = loanPcesMgmtNo;
    }

    public String getEvntNm() {
        return evntNm;
    }

    public void setEvntNm(String evntNm) {
        this.evntNm = evntNm;
    }

    public String getPrcsRsltCntn() {
        return prcsRsltCntn;
    }

    public void setPrcsRsltCntn(String prcsRsltCntn) {
        this.prcsRsltCntn = prcsRsltCntn;
    }
}
