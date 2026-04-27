package org.uengine.five.messaging.polling;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.messaging.EventOutbox;
import org.uengine.five.messaging.EventOutboxRepository;
import org.uengine.five.repository.EventMappingRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 외부 시스템 HTTP Inbox. 폴링 모드에서 Kafka 의 외부 이벤트 인입 경로를 대체한다.
 *
 * <pre>
 * POST /events/inbox
 *   Header: X-Event-Type: START_CREDIT_RATING
 *   Header: X-Corr-Key:   app-2026-001       (비즈니스 ID, 멱등성 키 겸용)
 *   Body:   { "applicationId": "app-2026-001", "자산": 5173, "신용도": 400 }
 * </pre>
 *
 * <p>BPM_EVENT_OUTBOX 의 (corr_key, event_type) 복합 UNIQUE 제약으로:
 * <ul>
 *   <li>같은 corr_key + 같은 event_type 재전송 → 멱등 차단 (중복 처리 방지)</li>
 *   <li>같은 corr_key + 다른 event_type → 허용 (트랜잭션의 시퀀스 이벤트)</li>
 * </ul>
 *
 * <p>X-Corr-Key 헤더가 없으면 EventMapping 의 correlation_key 가 가리키는 payload 필드에서
 * 자동 추출. 둘 다 없으면 corr_key=NULL (멱등 보장 없음).
 */
@RestController
@RequestMapping("/events")
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxController {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventInboxController.class);

    private final EventOutboxRepository repo;
    private final EventMappingRepository eventMappingRepository;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public ExternalEventInboxController(EventOutboxRepository repo,
                                        EventMappingRepository eventMappingRepository) {
        this.repo = repo;
        this.eventMappingRepository = eventMappingRepository;
    }

    @PostMapping("/inbox")
    @Transactional
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(name = "X-Event-Type", required = false) String type,
            @RequestHeader(name = "X-Corr-Key",   required = false) String corrKey,
            @RequestBody JsonNode body) {

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid payload: " + e.getMessage()));
        }

        // 헤더에 corr_key 없으면 EventMapping 으로 payload 에서 추출 시도
        if (corrKey == null && type != null) {
            corrKey = extractCorrKeyFromPayload(type, body);
        }

        EventOutbox ev = new EventOutbox();
        ev.setEventType(type);
        ev.setPayload(payloadJson);
        ev.setCorrKey(corrKey);

        try {
            repo.save(ev);
        } catch (DataIntegrityViolationException dup) {
            // (corr_key, event_type) 복합 유니크 위반 = 동일 트랜잭션의 동일 이벤트 재전송 = 멱등
            log.info("[inbox] duplicate (corrKey={}, type={}), treated as idempotent success", corrKey, type);
            return ResponseEntity.accepted().body(Map.of(
                    "status", "duplicate", "corrKey", corrKey, "type", type));
        }

        return ResponseEntity.accepted().body(Map.of("id", ev.getId(), "status", "queued"));
    }

    /**
     * EventMapping 에서 correlation_key 필드명을 읽고 payload 에서 그 값을 꺼낸다.
     * 매핑이 없거나 필드가 비어있으면 null.
     */
    private String extractCorrKeyFromPayload(String eventType, JsonNode body) {
        try {
            EventMappingEntity mapping = eventMappingRepository.findEventMappingByEventType(eventType);
            if (mapping == null || mapping.getCorrelationKey() == null) return null;
            JsonNode field = body.get(mapping.getCorrelationKey());
            return (field != null && !field.isNull()) ? field.asText() : null;
        } catch (Exception e) {
            log.warn("[inbox] failed to extract corrKey from payload for type={}", eventType, e);
            return null;
        }
    }
}
