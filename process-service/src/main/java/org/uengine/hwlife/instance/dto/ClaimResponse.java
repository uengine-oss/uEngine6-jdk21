package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

import org.uengine.five.dto.WorkItemResource;

/**
 * 다중 선점/선점 해제 응답.
 */
public class ClaimResponse {

    private int total;
    private int successCount;
    private int failureCount;
    private List<Item> results = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<Item> getResults() {
        return results;
    }

    public void setResults(List<Item> results) {
        this.results = results;
    }

    public void addSuccess(String taskId, WorkItemResource workItem) {
        successCount++;
        results.add(new Item(taskId, true, null, workItem));
    }

    public void addFailure(String taskId, String reason) {
        failureCount++;
        results.add(new Item(taskId, false, reason, null));
    }

    public static class Item {
        private String taskId;
        private boolean success;
        private String reason;
        private WorkItemResource workItem;

        public Item() {
        }

        public Item(String taskId, boolean success, String reason, WorkItemResource workItem) {
            this.taskId = taskId;
            this.success = success;
            this.reason = reason;
            this.workItem = workItem;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public WorkItemResource getWorkItem() {
            return workItem;
        }

        public void setWorkItem(WorkItemResource workItem) {
            this.workItem = workItem;
        }
    }
}
