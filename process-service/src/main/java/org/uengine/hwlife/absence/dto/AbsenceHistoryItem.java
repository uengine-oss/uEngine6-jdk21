package org.uengine.hwlife.absence.dto;

import java.util.Date;

/**
 * 부재 이력 항목 — {@link AbsenceHistoryResponse#getAbscList()} 요소.
 */
public class AbsenceHistoryItem {

    private String fncgBpmAbstSqno;
    private String abscEmnb;
    private String agntEmnb;
    private String agntFncgOrgnCode;
    private Date abscStarDttm;
    private Date abscEndDttm;
    private Date abscRscsDttm;
    private Date abscStupDttm;

    public String getFncgBpmAbstSqno() {
        return fncgBpmAbstSqno;
    }

    public void setFncgBpmAbstSqno(String fncgBpmAbstSqno) {
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
