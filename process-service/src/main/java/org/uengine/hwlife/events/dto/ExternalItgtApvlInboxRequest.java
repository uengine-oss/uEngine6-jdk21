package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 통합승인(ItgtApvl) Event Inbox — ESB 요청 {@code payload} 업무 DTO.
 *
 * <p>{@code /inbox-apvl} 전용. 필수값은 {@code evntNm}.
 * {@code loanPcesMgmtNo}(=corrKey) 가 없으면 Inbox INSERT 후
 * {@code 20 + yyyyMMdd + (BPM_EVENT_INBOX.id % 1000000)(6자리)} 로 채번한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalItgtApvlInboxRequest {

    /** 대출프로세스관리번호 */
    private String loanPcesMgmtNo;

    /** 이벤트명 */
    private String evntNm;

    /** 대출계약번호 */
    private String loanCntcNo;

    /** BPM 업무분류코드 — 없으면 enrich 시 기본값 {@code 20} */
    private String bpmBswrClsfCode;

    /** 업무내용 */
    private String bswrCntn;

    public String getLoanPcesMgmtNo() { return loanPcesMgmtNo; }
    public void setLoanPcesMgmtNo(String loanPcesMgmtNo) { this.loanPcesMgmtNo = loanPcesMgmtNo; }

    public String getEvntNm() { return evntNm; }
    public void setEvntNm(String evntNm) { this.evntNm = evntNm; }

    public String getLoanCntcNo() { return loanCntcNo; }
    public void setLoanCntcNo(String loanCntcNo) { this.loanCntcNo = loanCntcNo; }

    public String getBpmBswrClsfCode() { return bpmBswrClsfCode; }
    public void setBpmBswrClsfCode(String bpmBswrClsfCode) { this.bpmBswrClsfCode = bpmBswrClsfCode; }

    public String getBswrCntn() { return bswrCntn; }
    public void setBswrCntn(String bswrCntn) { this.bswrCntn = bswrCntn; }
}
