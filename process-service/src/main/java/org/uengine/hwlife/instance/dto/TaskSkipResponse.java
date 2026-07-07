package org.uengine.hwlife.instance.dto;

import java.util.List;

import org.uengine.five.dto.TaskSkipResult;

/**
 * 단위업무 SKIP 응답 — POST /instance/skip.
 */
public class TaskSkipResponse {

    private String instanceId;
    private Long rootInstId;
    private String skippedTracingTag;
    private List<Long> currentTaskIds;

    public static TaskSkipResponse from(TaskSkipResult engine) {
        TaskSkipResponse response = new TaskSkipResponse();
        response.setInstanceId(engine.getInstanceId());
        response.setRootInstId(engine.getRootInstId());
        response.setSkippedTracingTag(engine.getSkippedTracingTag());
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

    public String getSkippedTracingTag() {
        return skippedTracingTag;
    }

    public void setSkippedTracingTag(String skippedTracingTag) {
        this.skippedTracingTag = skippedTracingTag;
    }

    public List<Long> getCurrentTaskIds() {
        return currentTaskIds;
    }

    public void setCurrentTaskIds(List<Long> currentTaskIds) {
        this.currentTaskIds = currentTaskIds;
    }
}
