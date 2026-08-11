package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 조직 진행 건 검색 요청 — POST /search/org-running JSON body.
 * - 요청기관: 해당 업무를 최초로 시작한 기관 (rqstDvsnCode == Y AND fncgWndwOrgnCode == bpm_procinst.init_group_cd )
 * - 진행기관: 현재 단위업무를 진행 하고 있는 기관 (rqstDvsnCode == N AND fncgWndwOrgnCode == bpm_procinst.curr_group_cd)
 * - fncgWndwOrgnCode 가 없으면 header.belnOrgnCode 사용, 둘 다 없으면 빈 목록 응답
 * - rqstDvsnCode 기본값 N, pageSize 기본값 20, 조회기간 기본값 (오늘-30일)~오늘
 */
public class OrgRunningRequest {

    private String bpmBswrClsfCode;

    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstStarDate;
    @JsonFormat(pattern = "yyyyMMdd")
    private Date rqstEndDate;

    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String loanCntcNo;
    private String custId;
    private String fncgWndwOrgnCode;
    private String rqstDvsnCode;
    private String sortOrdrVal;
    private String fncgBpmPcesId;
    private String uworNm;

    private String nextKey;
    private Integer pageSize;

    public String getBpmBswrClsfCode() {
        return bpmBswrClsfCode;
    }

    public void setBpmBswrClsfCode(String bpmBswrClsfCode) {
        this.bpmBswrClsfCode = bpmBswrClsfCode;
    }

    public Date getRqstStarDate() {
        return rqstStarDate;
    }

    public void setRqstStarDate(Date rqstStarDate) {
        this.rqstStarDate = rqstStarDate;
    }

    public Date getRqstEndDate() {
        return rqstEndDate;
    }

    public void setRqstEndDate(Date rqstEndDate) {
        this.rqstEndDate = rqstEndDate;
    }

    public String getFncgSuptTrgtDvsnCode() {
        return fncgSuptTrgtDvsnCode;
    }

    public void setFncgSuptTrgtDvsnCode(String fncgSuptTrgtDvsnCode) {
        this.fncgSuptTrgtDvsnCode = fncgSuptTrgtDvsnCode;
    }

    public String getLoanSubjDvsnCode() {
        return loanSubjDvsnCode;
    }

    public void setLoanSubjDvsnCode(String loanSubjDvsnCode) {
        this.loanSubjDvsnCode = loanSubjDvsnCode;
    }

    public String getFncgMneyUsagClsfCode() {
        return fncgMneyUsagClsfCode;
    }

    public void setFncgMneyUsagClsfCode(String fncgMneyUsagClsfCode) {
        this.fncgMneyUsagClsfCode = fncgMneyUsagClsfCode;
    }

    public String getLoanCntcNo() {
        return loanCntcNo;
    }

    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
    }

    public String getFncgWndwOrgnCode() {
        return fncgWndwOrgnCode;
    }

    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) {
        this.fncgWndwOrgnCode = fncgWndwOrgnCode;
    }

    public String getRqstDvsnCode() {
        return rqstDvsnCode;
    }

    public void setRqstDvsnCode(String rqstDvsnCode) {
        this.rqstDvsnCode = rqstDvsnCode;
    }

    public String getSortOrdrVal() {
        return sortOrdrVal;
    }

    public void setSortOrdrVal(String sortOrdrVal) {
        this.sortOrdrVal = sortOrdrVal;
    }
    public String getFncgBpmPcesId() {
        return fncgBpmPcesId;
    }

    public void setFncgBpmPcesId(String fncgBpmPcesId) {
        this.fncgBpmPcesId = fncgBpmPcesId;
    }

    public String getUworNm() {
        return uworNm;
    }

    public void setUworNm(String uworNm) {
        this.uworNm = uworNm;
    }

    public String getNextKey() {
        return nextKey;
    }

    public void setNextKey(String nextKey) {
        this.nextKey = nextKey;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
