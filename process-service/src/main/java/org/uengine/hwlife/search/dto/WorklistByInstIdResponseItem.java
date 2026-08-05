package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 *  인스턴스 기준  업무 목록  응답
 */
public class WorklistByInstIdResponseItem {

    private String fncgBpmTaskTrcgNm; // 단위업무 trc_tag
    private String uworNm; // 단위업무 명
    private String hndrEmnb; // 처리자 사번 (roleName)
    private String hndrNm; // 처리자 명(endpoint)
    private String hndrOrgnCode; // 처리자 기관코드 
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss")
    private Date uworStarDttm; // 단위업무 시작일 (WORK-ITEM)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss")
    private Date uworEndDttm; // 단위업무 종료일 
    private String fncgBpmUworSttsCntn; // 상태(WORK_ITEM)
    private String fncgBpmTaskLstId; // 태스크ID
    private String fncgBpmPcesIntcId; // 인스턴스 ID

    public String getFncgBpmTaskTrcgNm() {
        return fncgBpmTaskTrcgNm;
    }
    public void setFncgBpmTaskTrcgNm(String fncgBpmTaskTrcgNm) {
        this.fncgBpmTaskTrcgNm = fncgBpmTaskTrcgNm;
    }
    public String getUworNm() {
        return uworNm;
    }
    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
    }
    public String getHndrEmnb() {
        return hndrEmnb;
    }
    public void setHndrEmnb(String hndrEmnb) {
        this.hndrEmnb = hndrEmnb;
    }
    public String getHndrNm() {
        return hndrNm;
    }
    public void setHndrNm(String hndrNm) {
        this.hndrNm = hndrNm;
    }
    public String getHndrOrgnCode() {
        return hndrOrgnCode;
    }
    public void setHndrOrgnCode(String hndrOrgnCode) {
        this.hndrOrgnCode = hndrOrgnCode;
    }
    public Date getUworStarDttm() {
        return uworStarDttm;
    }
    public void setUworStarDttm(Date uworStarDttm) {
        this.uworStarDttm = uworStarDttm;
    }
    public Date getUworEndDttm() {
        return uworEndDttm;
    }
    public void setUworEndDttm(Date uworEndDttm) {
        this.uworEndDttm = uworEndDttm;
    }
    public String getFncgBpmUworSttsCntn() {
        return fncgBpmUworSttsCntn;
    }
    public void setFncgBpmUworSttsCntn(String fncgBpmUworSttsCntn) {
        this.fncgBpmUworSttsCntn = fncgBpmUworSttsCntn;
    }
    public String getFncgBpmTaskLstId() {
        return fncgBpmTaskLstId;
    }
    public void setFncgBpmTaskLstId(String fncgBpmTaskLstId) {
        this.fncgBpmTaskLstId = fncgBpmTaskLstId;
    }
    public String getFncgBpmPcesIntcId() {
        return fncgBpmPcesIntcId;
    }
    public void setFncgBpmPcesIntcId(String fncgBpmPcesIntcId) {
        this.fncgBpmPcesIntcId = fncgBpmPcesIntcId;
    }

   
}
