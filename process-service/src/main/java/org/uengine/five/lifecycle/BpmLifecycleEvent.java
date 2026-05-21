package org.uengine.five.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BPM 업무/프로세스 생명주기 이벤트 메시지 객체.
 * <p>
 * eventType 별 의미:
 * <ul>
 *   <li>{@link BpmLifecycleEventType#TASK_ASSIGNED}          - 업무 최초 배정</li>
 *   <li>{@link BpmLifecycleEventType#TASK_ASSIGNMENT_CHANGED} - 담당자 변경 (위임·재배정)</li>
 *   <li>{@link BpmLifecycleEventType#TASK_TERMINATED}        - 업무 종료 (완료·스킵·취소 등)</li>
 *   <li>{@link BpmLifecycleEventType#PROCESS_COMPLETED}      - 메인 프로세스 인스턴스 전체 종료</li>
 * </ul>
 */
public class BpmLifecycleEvent {

    private String eventType;
    private String endpoint;
    @JsonProperty("prev_endpoint")
    private String prevEndpoint;
    private Long taskId;
    private Long instanceId;
    private Long rootInstId;
    private String tracingTag;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getPrevEndpoint() { return prevEndpoint; }
    public void setPrevEndpoint(String prevEndpoint) { this.prevEndpoint = prevEndpoint; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Long getRootInstId() { return rootInstId; }
    public void setRootInstId(Long rootInstId) { this.rootInstId = rootInstId; }

    public String getTracingTag() { return tracingTag; }
    public void setTracingTag(String tracingTag) { this.tracingTag = tracingTag; }

    @Override
    public String toString() {
        return "BpmLifecycleEvent{" +
                "eventType='" + eventType + '\'' +
                ", taskId=" + taskId +
                ", instanceId=" + instanceId +
                ", rootInstId=" + rootInstId +
                ", tracingTag='" + tracingTag + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", prev_endpoint='" + prevEndpoint + '\'' +
                '}';
    }
}
