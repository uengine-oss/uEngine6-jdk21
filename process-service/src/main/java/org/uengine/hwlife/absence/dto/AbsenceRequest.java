package org.uengine.hwlife.absence.dto;

import java.util.Date;

/**
 * 부재 설정/해제 통합 요청.
 *
 * <p>{@code bswrDvsnVal}: {@link #BSWR_DVSN_REG} 설정, {@link #BSWR_DVSN_RLS} 해제.</p>
 */
public class AbsenceRequest {
    private Long fncgBpmAbstSqno; // 부재 설정 ID

    private String abscEmnb; // 부재자 사용자 ID

    private String agntEmnb; // 대리인 사용자 ID
    private String agntFncgOrgnCd; // 대리인 조직 코드

    private String abscStrDttm; // 부재 시작일시
    private String abscEndDttm; // 부재 종료일시
    private String abscRscsDttm; // 조기 종료(해제) 시각
    private String abscStupDttm; // 등록 시각

    private String bswrClsfCode;  /** 업무구분값 — 0(설정) / 1(해제) */

    public Long getFncgBpmAbstSqno() {
        return fncgBpmAbstSqno;
    }
    public void setFncgBpmAbstSqno(Long fncgBpmAbstSqno) {
        this.fncgBpmAbstSqno = fncgBpmAbstSqno;
    }
    public String getAbscEmnb() {
        return abscEmnb;
    }
    public void setAbscEmnb(String abscEmnb) {
        this.abscEmnb = abscEmnb;
    }
    public String getAgntEmnb() {
        return agntEmnb;
    }
    public void setAgntEmnb(String agntEmnb) {
        this.agntEmnb = agntEmnb;
    }
    public String getAgntFncgOrgnCd() {
        return agntFncgOrgnCd;
    }
    public void setAgntFncgOrgnCd(String agntFncgOrgnCd) {
        this.agntFncgOrgnCd = agntFncgOrgnCd;
    }
    public String getAbscStrDttm() {
        return abscStrDttm;
    }
    public void setAbscStrDttm(String abscStrDttm) {
        this.abscStrDttm = abscStrDttm;
    }
    public String getAbscEndDttm() {
        return abscEndDttm;
    }
    public void setAbscEndDttm(String abscEndDttm) {
        this.abscEndDttm = abscEndDttm;
    }
    public String getAbscRscsDttm() {
        return abscRscsDttm;
    }
    public void setAbscRscsDttm(String abscRscsDttm) {
        this.abscRscsDttm = abscRscsDttm;
    }
    public String getAbscStupDttm() {
        return abscStupDttm;
    }
    public void setAbscStupDttm(String abscStupDttm) {
        this.abscStupDttm = abscStupDttm;
    }
    public String getBswrClsfCode() {
        return bswrClsfCode;
    }
    public void setBswrClsfCode(String bswrClsfCode) {
        this.bswrClsfCode = bswrClsfCode;
    }

}
