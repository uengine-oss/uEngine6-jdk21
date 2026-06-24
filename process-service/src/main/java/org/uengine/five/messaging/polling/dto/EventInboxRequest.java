package org.uengine.five.messaging.polling.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

public class EventInboxRequest {
    @JsonAlias({ "eventname", "eventName" })
    private String eventName;

    @JsonAlias({ "corrkey", "corrKey" })
    private String corrKey;

    private JsonNode payload;

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getCorrKey() {
        return corrKey;
    }

    public void setCorrKey(String corrKey) {
        this.corrKey = corrKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}