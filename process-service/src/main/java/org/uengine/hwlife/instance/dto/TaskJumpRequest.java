package org.uengine.hwlife.instance.dto;

/**
 * 단위업무 점프(강제 이동) 요청 — POST /instance/jump.
 */
public class TaskJumpRequest {

    private String taskId;
    private String targetTracingTag;
    private String execScope;
    private String reason;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTargetTracingTag() {
        return targetTracingTag;
    }

    public void setTargetTracingTag(String targetTracingTag) {
        this.targetTracingTag = targetTracingTag;
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
