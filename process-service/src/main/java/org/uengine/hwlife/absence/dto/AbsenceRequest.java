package org.uengine.hwlife.absence.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.uengine.hwlife.esbclient.dto.EsbCodes;

/**
 * 부재 설정/해제 요청 — POST /absences JSON body.
 *
 * <p>부재자 사번은 body 가 아니라 ESB {@code header.emnb} 를 사용한다.</p>
 */
public class AbsenceRequest {

    private String fncgBpmAbstSqno;
    private String agntEmnb;
    private String agntFncgOrgnCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscStarDttm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscEndDttm;

    public String getFncgBpmAbstSqno() {
        return fncgBpmAbstSqno;
    }

    public void setFncgBpmAbstSqno(String fncgBpmAbstSqno) {
        this.fncgBpmAbstSqno = fncgBpmAbstSqno;
    }

    public String getAgntEmnb() {
        return agntEmnb;
    }

    public void setAgntEmnb(String agntEmnb) {
        this.agntEmnb = agntEmnb;
    }

    public String getAgntFncgOrgnCode() {
        return agntFncgOrgnCode;
    }

    public void setAgntFncgOrgnCode(String agntFncgOrgnCode) {
        this.agntFncgOrgnCode = agntFncgOrgnCode;
    }

    public Date getAbscStarDttm() {
        return abscStarDttm;
    }

    public void setAbscStarDttm(Date abscStarDttm) {
        this.abscStarDttm = abscStarDttm;
    }

    public Date getAbscEndDttm() {
        return abscEndDttm;
    }

    public void setAbscEndDttm(Date abscEndDttm) {
        this.abscEndDttm = abscEndDttm;
    }
}
