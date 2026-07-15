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
        if (isEventRequestWrapper(defaultType, defaultCorrKey, body)) {
            EventInboxRequest request;
            try {
                request = objectMapper.treeToValue(body, EventInboxRequest.class);
            } catch (Exception e) {
                return EventInboxResponse.failed(defaultType, defaultCorrKey, "invalid event: " + e.getMessage());
            }
            String type = request.getEventName() != null ? request.getEventName() : defaultType;
            String corrKey = request.getCorrKey() != null ? request.getCorrKey() : defaultCorrKey;
            JsonNode correlationBody = request.getPayload() != null ? request.getPayload() : body;
            return receiveEvent(type, corrKey, body, correlationBody,
                    request.getPrcrRsltCodeNm(), request.getPrcsRsltCntn());
        }
        return receiveEvent(defaultType, defaultCorrKey, body);
    }

    private boolean isEventRequestWrapper(String defaultType, String defaultCorrKey, JsonNode body) {
        if (body == null || !body.isObject()) {
            return false;
        }
        return body.has("eventName") || body.has("eventname") || body.has("eventNm") || body.has("evntNm")
                || body.has("corrKey") || body.has("corrkey") || body.has("loanPcesMgmtNo")
                || defaultType != null || defaultCorrKey != null;
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
            EventInboxResponse item = receiveEvent(type, corrKey, node, event.getPayload(),
                    event.getPrcrRsltCodeNm(), event.getPrcsRsltCntn());
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
        return receiveEvent(type, corrKey, body, body, null, null);
    }

    private EventInboxResponse receiveEvent(String type, String corrKey, JsonNode storedBody, JsonNode correlationBody,
                                            String prcrRsltCodeNm, String prcsRsltCntn) {
        String payloadJson;
        if (storedBody == null || storedBody.isMissingNode() || storedBody.isNull()) {
            payloadJson = "{}";
        } else {
            try {
                payloadJson = objectMapper.writeValueAsString(storedBody);
            } catch (Exception e) {
                return EventInboxResponse.failed(type, corrKey, "invalid payload: " + e.getMessage());
            }
        }

        if (corrKey == null && type != null && correlationBody != null && !correlationBody.isMissingNode() && !correlationBody.isNull()) {
            corrKey = extractCorrKeyFromPayload(type, correlationBody);
        }

        EventInbox ev = new EventInbox();
        ev.setEventName(type);
        ev.setPayload(payloadJson);
        ev.setCorrKey(corrKey);
        ev.setPrcrRsltCodeNm(prcrRsltCodeNm);
        ev.setPrcsRsltCntn(prcsRsltCntn);

        try {
            repo.save(ev);
        } catch (DataIntegrityViolationException dup) {
            EventInbox existing = findExistingInboxForDuplicate(corrKey, type);
            Instant existingCreatedAt = existing != null ? existing.getCreatedAt() : null;
            Long existingId = existing != null ? existing.getId() : null;
            log.info("[inbox] duplicate (corrKey={}, type={}, existingId={}), treated as idempotent success",
                    corrKey, type, existingId);
            EventInboxResponse response = EventInboxResponse.duplicate(type, corrKey, existingCreatedAt);
            if (existing != null) {
                response.setInboxId(existing.getId());
                response.setPrcrRsltCodeNm(existing.getPrcrRsltCodeNm());
                response.setPrcsRsltCntn(existing.getPrcsRsltCntn());
            }
            return response;
        }

        EventInboxResponse response = EventInboxResponse.success(type, corrKey, ev.getCreatedAt());
        response.setInboxId(ev.getId());
        response.setPrcrRsltCodeNm(ev.getPrcrRsltCodeNm());
        response.setPrcsRsltCntn(ev.getPrcsRsltCntn());
        return response;
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
