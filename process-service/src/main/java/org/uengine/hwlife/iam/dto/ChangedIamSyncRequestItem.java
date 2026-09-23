package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 요청.
 *
 * <p>{@code changeType}: {@code ORG_MERGE}(기관 통폐합), {@code HR_CHANGE}(인사변동)</p>
 */
public class ChangedIamSyncRequestItem {
    
    private String loanCntnNo;
    private String chngBefrFncgWndwOrgnCode;
    private String chngAftFncgWndwOrgnCode;

    public String getLoanCntnNo() {
        return loanCntnNo;
    }

    public void setLoanCntnNo(String loanCntnNo) {
        this.loanCntnNo = loanCntnNo;
    }

    public String getChngBefrFncgWndwOrgnCode() {
        return chngBefrFncgWndwOrgnCode;
    }

    public void setChngBefrFncgWndwOrgnCode(String chngBefrFncgWndwOrgnCode) {
        this.chngBefrFncgWndwOrgnCode = chngBefrFncgWndwOrgnCode;
    }

    public String getChngAftFncgWndwOrgnCode() {
        return chngAftFncgWndwOrgnCode;
    }
}

    

