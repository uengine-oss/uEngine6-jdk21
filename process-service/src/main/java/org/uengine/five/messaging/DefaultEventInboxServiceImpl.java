package org.uengine.five.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.DefaultEventInboxRequest;
import org.uengine.five.dto.DefaultEventInboxResponse;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기본(Default) Event Inbox 수신 서비스 구현.
 */
@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class DefaultEventInboxServiceImpl implements DefaultEventInboxService {

    private final EventInboxEnqueueService enqueueService;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public DefaultEventInboxServiceImpl(EventInboxEnqueueService enqueueService) {
        this.enqueueService = enqueueService;
    }

    @Override
    public EventInboxReceiveResult receiveEvent(String requestBodyJson) {
        String eventName = null;
        String corrKey = null;

        try {
            DefaultEventInboxRequest request = parseRequest(requestBodyJson);
            eventName = request.getEventName();
            corrKey = request.getCorrKey();
            String payloadJson = serializePayload(request.getPayload());
            EventInboxResponse coreResponse = enqueueService.enqueue(
                    new EventInboxRequest(eventName, corrKey, payloadJson));
            DefaultEventInboxResponse response = DefaultEventInboxResponse.from(coreResponse);
            if (EventInboxResponse.STATUS_FAILED.equals(coreResponse.getStatus())) {
                return EventInboxReceiveResult.failure(response);
            }
            return EventInboxReceiveResult.success(response);
        } catch (Exception e) {
            return EventInboxReceiveResult.failure(
                    DefaultEventInboxResponse.failed(eventName, corrKey, "invalid payload: " + e.getMessage()));
        }
    }

    private DefaultEventInboxRequest parseRequest(String requestBodyJson) throws Exception {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return new DefaultEventInboxRequest();
        }
        return objectMapper.readValue(requestBodyJson, DefaultEventInboxRequest.class);
    }

    private String serializePayload(JsonNode payload) throws Exception {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(payload);
    }
}
