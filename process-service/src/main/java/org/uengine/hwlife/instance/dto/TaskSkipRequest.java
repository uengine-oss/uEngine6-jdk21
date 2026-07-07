package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 SKIP 요청 — POST /instance/skip.
 */
public class TaskSkipRequest {

    private String taskId;
    private String reason;

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
