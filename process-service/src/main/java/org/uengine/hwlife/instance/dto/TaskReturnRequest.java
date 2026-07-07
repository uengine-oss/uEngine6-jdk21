package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 반송(이전 단계) 요청 — POST /instance/return.
 */
public class TaskReturnRequest {

    private String taskId;
    private Long targetTaskId;
    private String tracingTag;
    private String execScope;
    private String reason;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getTargetTaskId() {
        return targetTaskId;
    }

    public void setTargetTaskId(Long targetTaskId) {
        this.targetTaskId = targetTaskId;
    }

    public String getTracingTag() {
        return tracingTag;
    }

    public void setTracingTag(String tracingTag) {
        this.tracingTag = tracingTag;
    }

    public String getExecScope() {
        return execScope;
    }

    public void setExecScope(String execScope) {
        this.execScope = execScope;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
