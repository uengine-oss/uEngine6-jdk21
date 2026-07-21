package org.uengine.five.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 기본(Default) Event Inbox 응답 DTO.
 *
 * <p>공통 {@link EventInboxResponse} 를 HTTP 응답 형식으로 변환한 결과이다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefaultEventInboxResponse {

    private String status;

    private boolean duplicate;

    private String eventName;

    private String corrKey;

    private String reason;

    private Instant occurredAt;

    public static DefaultEventInboxResponse from(EventInboxResponse core) {
        DefaultEventInboxResponse r = new DefaultEventInboxResponse();
        r.status = core.getStatus();
        r.duplicate = core.isDuplicate();
        r.eventName = core.getEventName();
        r.corrKey = core.getCorrKey();
        r.reason = core.getReason();
        r.occurredAt = core.getOccurredAt();
        return r;
    }

    public static DefaultEventInboxResponse failed(String eventName, String corrKey, String reason) {
        return from(EventInboxResponse.failed(eventName, corrKey, reason));
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
