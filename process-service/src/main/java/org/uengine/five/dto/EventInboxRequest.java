package org.uengine.five.dto;

/**
 * Event Inbox 공통 인입 요청 DTO.
 *
 * <p>{@link org.uengine.five.messaging.EventInboxEnqueueService} 입력이다.
 * Default/External Provider 가 각자 HTTP 요청을 파싱한 뒤 이 형식으로 정규화한다.</p>
 */
public class EventInboxRequest {

    private String eventName;

    private String corrKey;

    private String payloadJson;

    public EventInboxRequest() {
    }

    public EventInboxRequest(String eventName, String corrKey, String payloadJson) {
        this.eventName = eventName;
        this.corrKey = corrKey;
        this.payloadJson = payloadJson;
    }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
