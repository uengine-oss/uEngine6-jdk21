package org.uengine.five.messaging.polling;

import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.messaging.polling.dto.EventInboxBulkResponse;

import com.fasterxml.jackson.databind.JsonNode;

public interface ExternalEventInboxService {
    Object receive(String defaultType, String defaultCorrKey, JsonNode body);

    EventInboxResponse receiveEvent(String type, String corrKey, JsonNode body);

    EventInboxBulkResponse receiveEvents(String defaultType, String defaultCorrKey, JsonNode body);
}