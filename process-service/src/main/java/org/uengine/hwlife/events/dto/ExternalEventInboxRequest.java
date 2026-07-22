package org.uengine.hwlife.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 외부 시스템 이벤트 Inbox — ESB 요청 {@code payload} 업무 DTO.
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
 *   <li>payload    ← 요청 JSON 의 {@code payload} 값 원문 (String)</li>
 * </ul>
 * 이 DTO 는 필수값 검증용이다. DB 저장은 DTO 재직렬화가 아니라 요청 {@code payload}
 * 원문(String)을 사용하며, 검증도 그 원문 문자열을 {@code readValue} 로 파싱한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalEventInboxRequest {

    /** 대출프로세스관리번호 */
    private String loanPcesMgmtNo;

    /** 이벤트명 */
    private String evntNm;

    /** 대출계약번호 */
    private String loanCntcNo;

    /** 융자업무구분코드 */
    private String fncgBswrDvsnCode;

    /** 융자지원대상구분코드 */
    private String fncgSuptTrgtDvsnCode;

    /** 대출과목구분코드 */
    private String loanSubjDvsnCode;

    /** 고객ID */
    private String custId;

    /** RT송금구분코드 */
    private String rtRemtDvsnCode;

    /** 법무사구분송금여부 */
    private String jdscDvsnRemtYn;

    /** 대환여부 */
    private String srpyYn;

    /** RT송금여부 */
    private String rtRemtYn;

    /** MCI 가입여부 */
    private String mciJoinYn;

    /** 고객수용여부 */
    private String custExppYn;

    /** 보전조치여부 */
    private String itgtActnYn;

    /** 대출방법구분코드 */
    private String lamdDvsnCode;

    /** 대출한도금액 */
    private BigDecimal loanLmitAmt;

    /** 대출잔액 */
    private BigDecimal loanBlmt;

    /** 대출총합계금액 */
    private BigDecimal loanSumAmt;

    /** 융자심사결과코드 */
    private String fncgJdgnRsltCode;

    /** 대출승인요청구분코드 */
    private String loanAprvRqstDvsnCode;

    /** 융자자금용도분류코드 */
    private String fncgMneyUsagClsfCode;

    /** 접수 처리자 사원번호 */
    private String reptHndrEmnb;

    /** 심사자 사원번호 */
    private String unwrEmnb;

    /** 담당자사원번호 */
    private String prchEmnb;

    /** 융자창구기관코드 */
    private String fncgWndwOrgnCode;

    /** 상위기관코드 */
    private String hgrnOrgnCode;

    /** 대출희망일자 */
    private Date loanHopeDate;

    /** 역량난이도레벨명 */
    private String cpabLvdfLvelNm;

    // /** MI 가입여부 */
    // private String miJoinYn;

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

    public String getLoanSubjDvsnCode() { return loanSubjDvsnCode; }
    public void setLoanSubjDvsnCode(String loanSubjDvsnCode) { this.loanSubjDvsnCode = loanSubjDvsnCode; }

    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }

    public String getRtRemtDvsnCode() { return rtRemtDvsnCode; }
    public void setRtRemtDvsnCode(String rtRemtDvsnCode) { this.rtRemtDvsnCode = rtRemtDvsnCode; }

    public String getJdscDvsnRemtYn() { return jdscDvsnRemtYn; }
    public void setJdscDvsnRemtYn(String jdscDvsnRemtYn) { this.jdscDvsnRemtYn = jdscDvsnRemtYn; }

    public String getSrpyYn() { return srpyYn; }
    public void setSrpyYn(String srpyYn) { this.srpyYn = srpyYn; }

    public String getRtRemtYn() { return rtRemtYn; }
    public void setRtRemtYn(String rtRemtYn) { this.rtRemtYn = rtRemtYn; }

    public String getMciJoinYn() { return mciJoinYn; }
    public void setMciJoinYn(String mciJoinYn) { this.mciJoinYn = mciJoinYn; }

    public String getCustExppYn() { return custExppYn; }
    public void setCustExppYn(String custExppYn) { this.custExppYn = custExppYn; }

    public String getItgtActnYn() { return itgtActnYn; }
    public void setItgtActnYn(String itgtActnYn) { this.itgtActnYn = itgtActnYn; }

    public String getLamdDvsnCode() { return lamdDvsnCode; }
    public void setLamdDvsnCode(String lamdDvsnCode) { this.lamdDvsnCode = lamdDvsnCode; }

    public BigDecimal getLoanLmitAmt() { return loanLmitAmt; }
    public void setLoanLmitAmt(BigDecimal loanLmitAmt) { this.loanLmitAmt = loanLmitAmt; }

    public BigDecimal getLoanBlmt() { return loanBlmt; }
    public void setLoanBlmt(BigDecimal loanBlmt) { this.loanBlmt = loanBlmt; }

    public BigDecimal getLoanSumAmt() { return loanSumAmt; }
    public void setLoanSumAmt(BigDecimal loanSumAmt) { this.loanSumAmt = loanSumAmt; }

    public String getFncgJdgnRsltCode() { return fncgJdgnRsltCode; }
    public void setFncgJdgnRsltCode(String fncgJdgnRsltCode) { this.fncgJdgnRsltCode = fncgJdgnRsltCode; }

    public String getLoanAprvRqstDvsnCode() { return loanAprvRqstDvsnCode; }
    public void setLoanAprvRqstDvsnCode(String loanAprvRqstDvsnCode) { this.loanAprvRqstDvsnCode = loanAprvRqstDvsnCode; }

    public String getFncgMneyUsagClsfCode() { return fncgMneyUsagClsfCode; }
    public void setFncgMneyUsagClsfCode(String fncgMneyUsagClsfCode) { this.fncgMneyUsagClsfCode = fncgMneyUsagClsfCode; }

    public String getReptHndrEmnb() { return reptHndrEmnb; }
    public void setReptHndrEmnb(String reptHndrEmnb) { this.reptHndrEmnb = reptHndrEmnb; }

    public String getUnwrEmnb() { return unwrEmnb; }
    public void setUnwrEmnb(String unwrEmnb) { this.unwrEmnb = unwrEmnb; }

    public String getPrchEmnb() { return prchEmnb; }
    public void setPrchEmnb(String prchEmnb) { this.prchEmnb = prchEmnb; }

    public String getFncgWndwOrgnCode() { return fncgWndwOrgnCode; }
    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) { this.fncgWndwOrgnCode = fncgWndwOrgnCode; }

    public String getHgrnOrgnCode() { return hgrnOrgnCode; }
    public void setHgrnOrgnCode(String hgrnOrgnCode) { this.hgrnOrgnCode = hgrnOrgnCode; }

    public Date getLoanHopeDate() { return loanHopeDate; }
    public void setLoanHopeDate(Date loanHopeDate) { this.loanHopeDate = loanHopeDate; }

    public String getCpabLvdfLvelNm() { return cpabLvdfLvelNm; }
    public void setCpabLvdfLvelNm(String cpabLvdfLvelNm) { this.cpabLvdfLvelNm = cpabLvdfLvelNm; }

    // public String getMiJoinYn() { return miJoinYn; }
    // public void setMiJoinYn(String miJoinYn) { this.miJoinYn = miJoinYn; }
}
