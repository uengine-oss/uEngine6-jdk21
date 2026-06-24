package org.uengine.five.messaging.polling;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.messaging.polling.dto.EventInboxBulkResponse;
import org.uengine.five.messaging.polling.dto.EventInboxFailure;
import org.uengine.five.messaging.polling.dto.EventInboxRequest;
import org.uengine.five.repository.EventMappingRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxServiceImpl implements ExternalEventInboxService {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventInboxServiceImpl.class);

    private final EventInboxRepository repo;
    private final EventMappingRepository eventMappingRepository;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public ExternalEventInboxServiceImpl(EventInboxRepository repo,
                                         EventMappingRepository eventMappingRepository) {
        this.repo = repo;
        this.eventMappingRepository = eventMappingRepository;
    }

    @Override
    public Object receive(String defaultType, String defaultCorrKey, JsonNode body) {
        if (body != null && body.isArray()) {
            return receiveEvents(defaultType, defaultCorrKey, body);
        }
        if (isEventRequestWrapper(body)) {
            EventInboxRequest request;
            try {
                request = objectMapper.treeToValue(body, EventInboxRequest.class);
            } catch (Exception e) {
                return EventInboxResponse.failed(defaultType, defaultCorrKey, "invalid event: " + e.getMessage());
            }
            String type = request.getEventName() != null ? request.getEventName() : defaultType;
            String corrKey = request.getCorrKey() != null ? request.getCorrKey() : defaultCorrKey;
            return receiveEvent(type, corrKey, request.getPayload());
        }
        return receiveEvent(defaultType, defaultCorrKey, body);
    }

    private boolean isEventRequestWrapper(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("payload")) {
            return false;
        }
        return body.has("eventName") || body.has("eventname") || body.has("corrKey") || body.has("corrkey");
    }

    @Override
    public EventInboxBulkResponse receiveEvents(String defaultType, String defaultCorrKey, JsonNode body) {
        EventInboxBulkResponse response = new EventInboxBulkResponse();
        if (body == null || body.isMissingNode() || body.isNull() || !body.isArray() || body.isEmpty()) {
            response.setStatus(EventInboxResponse.STATUS_SUCCESS);
            return response;
        }

        for (JsonNode node : body) {
            if (node == null || node.isNull()) {
                response.addFailure(new EventInboxFailure(null, null, null, "0", "event must not be null"));
                continue;
            }

            EventInboxRequest event;
            try {
                event = objectMapper.treeToValue(node, EventInboxRequest.class);
            } catch (Exception e) {
                response.addFailure(new EventInboxFailure(null, null, node, "0", "invalid event: " + e.getMessage()));
                continue;
            }

            String type = event.getEventName() != null ? event.getEventName() : defaultType;
            String corrKey = event.getCorrKey() != null ? event.getCorrKey() : defaultCorrKey;
            EventInboxResponse item = receiveEvent(type, corrKey, event.getPayload());
            if (EventInboxResponse.STATUS_SUCCESS.equals(item.getStatus())) {
                response.incrementSuccessCount();
            } else {
                response.addFailure(new EventInboxFailure(corrKey, type, event.getPayload(), "0", item.getReason()));
            }
        }

        response.setStatus(response.getFailCount() == 0 ? EventInboxResponse.STATUS_SUCCESS : "FAIL");
        return response;
    }

    @Override
    public EventInboxResponse receiveEvent(String type, String corrKey, JsonNode body) {
        String payloadJson;
        if (body == null || body.isMissingNode() || body.isNull()) {
            payloadJson = "{}";
        } else {
            try {
                payloadJson = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                return EventInboxResponse.failed(type, corrKey, "invalid payload: " + e.getMessage());
            }
        }

        if (corrKey == null && type != null && body != null && !body.isMissingNode() && !body.isNull()) {
            corrKey = extractCorrKeyFromPayload(type, body);
        }

        EventInbox ev = new EventInbox();
        ev.setEventName(type);
        ev.setPayload(payloadJson);
        ev.setCorrKey(corrKey);

        try {
            repo.save(ev);
        } catch (DataIntegrityViolationException dup) {
            EventInbox existing = findExistingInboxForDuplicate(corrKey, type);
            Instant existingCreatedAt = existing != null ? existing.getCreatedAt() : null;
            Long existingId = existing != null ? existing.getId() : null;
            log.info("[inbox] duplicate (corrKey={}, type={}, existingId={}), treated as idempotent success",
                    corrKey, type, existingId);
            return EventInboxResponse.duplicate(type, corrKey, existingCreatedAt);
        }

        return EventInboxResponse.success(type, corrKey, ev.getCreatedAt());
    }

    private EventInbox findExistingInboxForDuplicate(String corrKey, String eventName) {
        if (corrKey == null || eventName == null) {
            return null;
        }
        return repo.findFirstByCorrKeyAndEventName(corrKey, eventName).orElse(null);
    }

    private String extractCorrKeyFromPayload(String eventType, JsonNode body) {
        try {
            EventMappingEntity mapping = eventMappingRepository.findEventMappingByEventName(eventType);
            if (mapping == null || mapping.getCorrelationKey() == null) {
                return null;
            }
            JsonNode field = body.get(mapping.getCorrelationKey());
            return (field != null && !field.isNull()) ? field.asText() : null;
        } catch (Exception e) {
            log.warn("[inbox] failed to extract corrKey from payload for type={}", eventType, e);
            return null;
        }
    }
}