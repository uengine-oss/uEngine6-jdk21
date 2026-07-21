package org.uengine.five.dto;

import java.util.ArrayList;
import java.util.List;

public class LoanStatusResponse {

    private String loanProcessManagementNo;
    private String processStatus;
    private List<LoanActiveWorkItemResponse> activeWorkItems = new ArrayList<>();

    public String getLoanProcessManagementNo() { return loanProcessManagementNo; }
    public void setLoanProcessManagementNo(String loanProcessManagementNo) { this.loanProcessManagementNo = loanProcessManagementNo; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
    public List<LoanActiveWorkItemResponse> getActiveWorkItems() { return activeWorkItems; }
    public void setActiveWorkItems(List<LoanActiveWorkItemResponse> activeWorkItems) { this.activeWorkItems = activeWorkItems; }
}
