package org.uengine.hwlife.instance.dto;

import java.util.List;

import org.uengine.five.dto.TaskReturnResult;

/**
 * 단위업무 반송 응답 — POST /instance/return.
 */
public class TaskReturnResponse {

    private String instanceId;
    private Long rootInstId;
    private String targetTracingTag;
    private List<Long> currentTaskIds;

    public static TaskReturnResponse from(TaskReturnResult engine) {
        TaskReturnResponse response = new TaskReturnResponse();
        response.setInstanceId(engine.getInstanceId());
        response.setRootInstId(engine.getRootInstId());
        response.setTargetTracingTag(engine.getTargetTracingTag());
        response.setCurrentTaskIds(engine.getCurrentTaskIds());
        return response;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Long getRootInstId() {
        return rootInstId;
    }

    public void setRootInstId(Long rootInstId) {
        this.rootInstId = rootInstId;
    }

    public String getTargetTracingTag() {
        return targetTracingTag;
    }

    public void setTargetTracingTag(String targetTracingTag) {
        this.targetTracingTag = targetTracingTag;
    }

    public List<Long> getCurrentTaskIds() {
        return currentTaskIds;
    }

    public void setCurrentTaskIds(List<Long> currentTaskIds) {
        this.currentTaskIds = currentTaskIds;
    }
}
