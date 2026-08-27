package org.uengine.hwlife.search.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.uengine.hwlife.esbclient.dto.EsbCodes;

/**
 * 나의 진행 검색 요청 — POST /search/my-progress JSON body.
 */
public class MyProgressRequest {

    private String bpmBswrClsfCode;
    @JsonFormat(pattern = EsbCodes.DATE)
    private Date rqstStarDate;
    @JsonFormat(pattern = EsbCodes.DATE)
    private Date rqstEndDate;
    private String fncgSuptTrgtDvsnCode;
    private String loanSubjDvsnCode;
    private String fncgMneyUsagClsfCode;
    private String custId;
    private String loanCntcNo;
    private String fncgWndwOrgnCode;
    private String sortOrdrVal;
    /** 메인/서브 루트 인스턴스 {@code defId} 필터. */
    private String bswrDvsnVal;
    /** 현재 단위업무 {@code worklist.defId} 필터. */
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
    
    public String getCustId() {
        return custId;
    }
    
    public void setCustId(String custId) {
        this.custId = custId;
    }
    
    public String getLoanCntcNo() {
        return loanCntcNo;
    }
    
    public void setLoanCntcNo(String loanCntcNo) {
        this.loanCntcNo = loanCntcNo;
    }
    
    public String getFncgWndwOrgnCode() {
        return fncgWndwOrgnCode;
    }
    
    public void setFncgWndwOrgnCode(String fncgWndwOrgnCode) {
        this.fncgWndwOrgnCode = fncgWndwOrgnCode;
    }
    
    public String getSortOrdrVal() {
        return sortOrdrVal;
    }
    
    public void setSortOrdrVal(String sortOrdrVal) {
        this.sortOrdrVal = sortOrdrVal;
    }
    
    public String getBswrDvsnVal() {
        return bswrDvsnVal;
    }

    public void setBswrDvsnVal(String bswrDvsnVal) {
        this.bswrDvsnVal = bswrDvsnVal;
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
