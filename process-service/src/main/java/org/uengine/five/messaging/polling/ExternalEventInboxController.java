package org.uengine.five.messaging.polling;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventOutbox;
import org.uengine.five.messaging.EventOutboxRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 외부 시스템 HTTP Inbox. 폴링 모드에서 Kafka 의 {@code bpm-in-0} 인입 경로를 대체한다.
 *
 * <pre>
 * POST /events/inbox
 *   Header: X-Event-Type: LOAN_APPLIED
 *   Header: X-Event-Id:  11111-uuid  (optional, 멱등성)
 *   Body:   { "applicantId": "u1", "amount": 1000, ... }
 * </pre>
 *
 * BPM_EVENT_OUTBOX 에 channel='bpm-in-0' 으로 INSERT 하면 OutboxEventPoller 가 꺼내서
 * 기존 BpmMessageDispatcher.wheneverEvent() → EventMapping 경로로 전달한다.
 */
@RestController
@RequestMapping("/events")
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxController {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventInboxController.class);

    private final EventOutboxRepository repo;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public ExternalEventInboxController(EventOutboxRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/inbox")
    @Transactional
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(name = "X-Event-Type", required = false) String type,
            @RequestHeader(name = "X-Event-Id",   required = false) String eventId,
            @RequestHeader(name = "X-Event-Key",  required = false) String correlationKey,
            @RequestBody JsonNode body) {

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid payload: " + e.getMessage()));
        }

        Map<String, Object> headers = new java.util.LinkedHashMap<>();
        if (type != null) headers.put("type", type);
        if (correlationKey != null) headers.put("correlationKey", correlationKey);
        if (eventId != null) headers.put("eventId", eventId);

        String headersJson;
        try {
            headersJson = objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            headersJson = null;
        }

        EventOutbox ev = new EventOutbox();
        ev.setChannel("bpm-in-0");
        ev.setEventType(type);
        ev.setPayload(payloadJson);
        ev.setHeaders(headersJson);
        ev.setEventId(eventId);

        try {
            repo.save(ev);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // 멱등성: 동일 eventId 재인입. 성공으로 간주.
            log.info("[inbox] duplicate eventId={}, treated as idempotent success", eventId);
            return ResponseEntity.accepted().body(Map.of("status", "duplicate", "eventId", eventId));
        }

        return ResponseEntity.accepted().body(Map.of("id", ev.getId(), "status", "queued"));
    }
}
