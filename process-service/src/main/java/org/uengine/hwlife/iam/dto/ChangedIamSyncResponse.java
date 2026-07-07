package org.uengine.hwlife.iam.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * IAM 변경(기관 통폐합·인사변동)에 따른 인스턴스·업무 DB 반영 결과.
 */
public class ChangedIamSyncResponse {

    private int targetCount;
    private int updatedCount;
    private int failedCount;
    private List<Item> failures = new ArrayList<>();

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<Item> getFailures() {
        return failures;
    }

    public void setFailures(List<Item> failures) {
        this.failures = failures != null ? failures : new ArrayList<>();
    }

    public static class Item {

        private Long instId;
        private String taskId;
        private String reason;

        public Long getInstId() {
            return instId;
        }

        public void setInstId(Long instId) {
            this.instId = instId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
