package org.uengine.five.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 기본(Default) Event Inbox 요청 DTO.
 *
 * <p>요청 형식:
 * <pre>
 * {
 *   "eventName": "...",
 *   "corrKey":   "...",
 *   "payload":   { ... }
 * }
 * </pre>
 *
 * <p>DB 저장 매핑:
 * <ul>
 *   <li>event_name ← {@link #eventName}</li>
 *   <li>corr_key   ← {@link #corrKey}</li>
 *   <li>payload    ← {@link #payload} JSON 직렬화</li>
 * </ul>
 */
public class DefaultEventInboxRequest {

    private String eventName;

    private String corrKey;

    private JsonNode payload;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
