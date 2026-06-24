package org.uengine.five.messaging.polling.dto;

import java.util.ArrayList;
import java.util.List;

public class EventInboxBulkResponse {
    private String status;
    private int successCount;
    private int failCount;
    private List<EventInboxFailure> failedList = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<EventInboxFailure> getFailedList() {
        return failedList;
    }

    public void setFailedList(List<EventInboxFailure> failedList) {
        this.failedList = failedList;
    }

    public void addFailure(EventInboxFailure failure) {
        failedList.add(failure);
        failCount++;
    }
}