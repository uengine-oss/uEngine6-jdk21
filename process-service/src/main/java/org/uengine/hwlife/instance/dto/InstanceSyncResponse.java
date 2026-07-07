package org.uengine.hwlife.instance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 업무(인스턴스·워크리스트) 상태 동기화 결과 — POST /instance/sync.
 */
public class InstanceSyncResponse {

    private int targetCount;
    private int syncedCount;
    private int failedCount;
    private List<Item> failures = new ArrayList<>();

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public int getSyncedCount() {
        return syncedCount;
    }

    public void setSyncedCount(int syncedCount) {
        this.syncedCount = syncedCount;
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
