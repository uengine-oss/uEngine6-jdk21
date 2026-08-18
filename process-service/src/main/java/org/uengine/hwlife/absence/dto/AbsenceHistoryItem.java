package org.uengine.hwlife.absence.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.uengine.hwlife.esbclient.dto.EsbCodes;

/**
 * 부재 이력 항목 — {@link AbsenceHistoryResponse#getAbscList()} 요소.
 */
public class AbsenceHistoryItem {

    private String fncgBpmAbstSqno;
    private String abstEmnb;
    private String agntEmnb;
    private String agntFncgOrgnCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscStarDttm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscEndDttm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscRscsDttm;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = EsbCodes.DTTM_SEC, timezone = "Asia/Seoul")
    private Date abscStupDttm;

    public String getFncgBpmAbstSqno() {
        return fncgBpmAbstSqno;
    }

    public void setFncgBpmAbstSqno(String fncgBpmAbstSqno) {
        this.fncgBpmAbstSqno = fncgBpmAbstSqno;
    }

    public String getAbstEmnb() {
        return abstEmnb;
    }

    public void setAbstEmnb(String abstEmnb) {
        this.abstEmnb = abstEmnb;
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

    public Date getAbscRscsDttm() {
        return abscRscsDttm;
    }

    public void setAbscRscsDttm(Date abscRscsDttm) {
        this.abscRscsDttm = abscRscsDttm;
    }

    public Date getAbscStupDttm() {
        return abscStupDttm;
    }

    public void setAbscStupDttm(Date abscStupDttm) {
        this.abscStupDttm = abscStupDttm;
    }
}
