package org.uengine.five.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 코어 Event Inbox 수신 서비스 구현.
 */
@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class EventInboxServiceImpl implements EventInboxService {

    private final EventInboxEnqueueService enqueueService;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public EventInboxServiceImpl(EventInboxEnqueueService enqueueService) {
        this.enqueueService = enqueueService;
    }

    @Override
    public EventInboxReceiveResult receiveEvent(String requestBodyJson) {
        String eventName = null;
        String corrKey = null;

        try {
            JsonNode body = parseBody(requestBodyJson);
            eventName = textOrNull(body, "eventName");
            corrKey = textOrNull(body, "corrKey");
            String payloadJson = serializePayload(body.get("payload"));
            EventInboxResponse response = enqueueService.enqueue(eventName, corrKey, payloadJson);
            if (EventInboxResponse.STATUS_FAILED.equals(response.getStatus())) {
                return EventInboxReceiveResult.failure(response);
            }
            return EventInboxReceiveResult.success(response);
        } catch (Exception e) {
            return EventInboxReceiveResult.failure(
                    EventInboxResponse.failed(eventName, corrKey, "invalid payload: " + e.getMessage()));
        }
    }

    private JsonNode parseBody(String requestBodyJson) throws Exception {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(requestBodyJson);
    }

    private String serializePayload(JsonNode payload) throws Exception {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}
