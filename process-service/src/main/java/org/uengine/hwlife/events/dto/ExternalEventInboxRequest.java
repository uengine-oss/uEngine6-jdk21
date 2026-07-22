package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 외부 시스템 이벤트 Inbox — ESB 요청의code payload} 업무 DTO.
 *
 * <p>ESB 전문은 {@code { "header": {...}, "payload": {...} }} 구조이며,
 * 이 클래스는 {@code payload} 부에만 해당한다.
 * 봉투 파싱은 {@link org.uengine.hwlife.esbclient.dto.EsbRequest} /
 * {@link org.uengine.hwlife.esbclient.support.EsbEnvelope} 를 사용한다.</p>
 *
 * <pre>
 * {
 *   "header":  { ... EsbCommonHeader ... },
 *   "payload": {
 *     "loanPcesMgmtNo": "...",
 *     "evntNm": "...",
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>DB 저장 매핑:
 * <ul>
 *   <li>corr_key   ← {@link #loanPcesMgmtNo}</li>
 *   <li>event_name ← {@link #evntNm}</li>
 *   <li>payload    ← 이 DTO 를 JSON 문자열로 직렬화</li>
 * </ul>
 * 필드는 추후 계속 추가될 수 있으므로 알 수 없는 필드는 무시한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalEventInboxRequest {

    private String loanPcesMgmtNo;

    private String evntNm;

    private String loanCntcNo;

    private String fncgBswrDvsnCode;

    private String fncgSuptTrgtDvsnCode;

    // 이후 필드 추가 예정

    public String getLoanPcesMgmtNo() { return loanPcesMgmtNo; }
    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) { this.loanPcesMgmtNo = loanPcesMgmtNo; }

    public String getEvntNm() { return evntNm; }
    public void setEvntNm(String evntNm) { this.evntNm = evntNm; }

    public String getLoanCntcNo() { return loanCntcNo; }
    public void setLoanCntcNo(String loanCntcNo) { this.loanCntcNo = loanCntcNo; }

    public String getFncgBswrDvsnCode() { return fncgBswrDvsnCode; }
    public void setFncgBswrDvsnCode(String fncgBswrDvsnCode) { this.fncgBswrDvsnCode = fncgBswrDvsnCode; }

    public String getFncgSuptTrgtDvsnCode() { return fncgSuptTrgtDvsnCode; }
    public void setFncgSuptTrgtDvsnCode(String fncgSuptTrgtDvsnCode) { this.fncgSuptTrgtDvsnCode = fncgSuptTrgtDvsnCode; }
}
