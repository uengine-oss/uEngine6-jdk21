package org.uengine.five.messaging.polling.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class EventInboxFailure {
    @JsonProperty("corrkey")
    private String corrKey;

    @JsonProperty("eventname")
    private String eventName;

    private JsonNode payload;
    private String reasonCode;
    private String reason;

    public EventInboxFailure() {
    }

    public EventInboxFailure(String corrKey, String eventName, JsonNode payload, String reasonCode, String reason) {
        this.corrKey = corrKey;
        this.eventName = eventName;
        this.payload = payload;
        this.reasonCode = reasonCode;
        this.reason = reason;
    }

    public String getCorrKey() {
        return corrKey;
    }

    public void setCorrKey(String corrKey) {
        this.corrKey = corrKey;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}