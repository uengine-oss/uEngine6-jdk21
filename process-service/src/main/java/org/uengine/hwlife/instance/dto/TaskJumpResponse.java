package org.uengine.hwlife.instance.dto;

import org.uengine.five.dto.InstanceResource;

/**
 * 단위업무 점프(강제 이동) 응답 — POST /instance/jump.
 */
public class TaskJumpResponse {

    private String instanceId;
    private String sourceTracingTag;
    private String targetTracingTag;

    public static TaskJumpResponse from(InstanceResource instance, TaskJumpRequest request) {
        TaskJumpResponse response = new TaskJumpResponse();
        if (instance != null) {
            response.setInstanceId(instance.getInstanceId());
        }
        if (request != null) {
            response.setTargetTracingTag(request.getTargetTracingTag());
        }
        return response;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getSourceTracingTag() {
        return sourceTracingTag;
    }

    public void setSourceTracingTag(String sourceTracingTag) {
        this.sourceTracingTag = sourceTracingTag;
    }

    public String getTargetTracingTag() {
        return targetTracingTag;
    }

    public void setTargetTracingTag(String targetTracingTag) {
        this.targetTracingTag = targetTracingTag;
    }
}
