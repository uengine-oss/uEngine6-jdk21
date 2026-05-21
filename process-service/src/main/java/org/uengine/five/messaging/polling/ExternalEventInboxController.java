package org.uengine.five.messaging.polling;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.dto.EventInboxResponse;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
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

    private final EventInboxRepository repo;
    private final EventMappingRepository eventMappingRepository;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public ExternalEventInboxController(EventInboxRepository repo,
                                        EventMappingRepository eventMappingRepository) {
        this.repo = repo;
        this.eventMappingRepository = eventMappingRepository;
    }

    /**
     * NOTE: 의도적으로 {@code @Transactional} 을 두지 않는다.
     * <ul>
     *   <li>Postgres 는 트랜잭션 안에서 UNIQUE 위반이 나면 해당 트랜잭션이 abort 되어
     *       이후 같은 트랜잭션에서 SELECT 를 못 함 → 중복 행의 시퀀스 id 조회가 불가능해진다.</li>
     *   <li>{@code repo.save(...)} / {@code repo.findFirstBy...} 모두 Spring Data 의 자체
     *       트랜잭션을 가지므로 컨트롤러 레벨 트랜잭션 없이도 일관성 문제 없음.</li>
     * </ul>
     */
    @PostMapping("/inbox")
    public ResponseEntity<EventInboxResponse> receiveEvent(
            @RequestHeader(name = "X-Event-Type", required = false) String type,
            @RequestHeader(name = "X-Corr-Key",   required = false) String corrKey,
            @RequestBody(required = false) JsonNode body) {

        // body 가 없는 신호성 이벤트도 허용: 빈 JSON 객체로 정규화한다.
        // payload 컬럼 NOT NULL 제약을 유지하면서 외부에서는 body 생략 가능.
        String payloadJson;
        if (body == null || body.isMissingNode()) {
            payloadJson = "{}";
        } else {
            try {
                payloadJson = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(EventInboxResponse.failed(type, corrKey, "invalid payload: " + e.getMessage()));
            }
        }

        // 헤더에 corr_key 없으면 EventMapping 으로 payload 에서 추출 시도 (body 없으면 자동으로 null)
        if (corrKey == null && type != null && body != null && !body.isMissingNode()) {
            corrKey = extractCorrKeyFromPayload(type, body);
        }

        EventInbox ev = new EventInbox();
        ev.setEventType(type);
        ev.setPayload(payloadJson);
        ev.setCorrKey(corrKey);

        try {
            repo.save(ev);
        } catch (DataIntegrityViolationException dup) {
            // (corr_key, event_type) 복합 유니크 위반 = 동일 트랜잭션의 동일 이벤트 재전송 = 멱등.
            // 응답 timestamp 는 기존 row 의 createdAt 으로 (= 처음 큐에 들어간 시각).
            // 운영 추적을 위해 시퀀스 id 는 로그에만 남긴다.
            EventInbox existing = findExistingInboxForDuplicate(corrKey, type);
            Long existingId = existing != null ? existing.getId() : null;
            Instant existingCreatedAt = existing != null ? existing.getCreatedAt() : null;
            log.info("[inbox] duplicate (corrKey={}, type={}, existingId={}), treated as idempotent success",
                    corrKey, type, existingId);
            return ResponseEntity.accepted()
                    .body(EventInboxResponse.duplicate(type, corrKey, existingCreatedAt));
        }

        return ResponseEntity.accepted()
                .body(EventInboxResponse.success(type, corrKey, ev.getCreatedAt()));
    }

    /**
     * UNIQUE (corr_key, event_type) 위반 시 응답에 넣을 기존 row 를 조회한다.
     * 둘 중 하나라도 null 이면 복합 키로 조회할 수 없어 null.
     */
    private EventInbox findExistingInboxForDuplicate(String corrKey, String eventType) {
        if (corrKey == null || eventType == null) {
            return null;
        }
        return repo.findFirstByCorrKeyAndEventType(corrKey, eventType).orElse(null);
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
