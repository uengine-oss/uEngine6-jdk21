package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventOutbox;
import org.uengine.five.messaging.EventOutboxRepository;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.five.messaging.TypedJsonObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 폴링 전략 발행자. process-service / definition-service 양쪽에서 공용.
 *
 * <ul>
 *   <li>{@code bpm-brodcast} (프론트 실시간 알림): DB 저장하지 않고 {@code SELECT pg_notify(...)}
 *       만 호출. LISTEN 중인 PgNotifyListener 가 받아 SSE 로 푸시.</li>
 *   <li>{@code bpm-out}, {@code bpm-in-0} (엔진 내부): {@code BPM_EVENT_OUTBOX} INSERT.
 *       OutboxEventPoller 가 꺼내 BpmMessageDispatcher 로 전달.</li>
 * </ul>
 *
 * 양쪽 경로 모두 호출 트랜잭션에 묶이므로, 도메인 롤백 시 발행도 자동 취소 (원자성 강화).
 */
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private static final String BRODCAST_CHANNEL = "bpm-brodcast";

    private final EventOutboxRepository repo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = TypedJsonObjectMapperFactory.create();

    public OutboxEventPublisher(EventOutboxRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void send(String channel, Object payload, Map<String, Object> headers) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payload for channel=" + channel, e);
        }

        String type = headers != null && headers.get("type") != null ? headers.get("type").toString() : null;

        if (BRODCAST_CHANNEL.equals(channel)) {
            publishBrodcast(channel, type, payloadJson);
            return;
        }

        EventOutbox ev = new EventOutbox();
        ev.setChannel(channel);
        ev.setEventType(type);
        ev.setPayload(payloadJson);
        ev.setHeaders(toJson(headers));
        ev.setEventId(headers != null && headers.get("eventId") != null ? headers.get("eventId").toString() : null);
        repo.save(ev);
    }

    private void publishBrodcast(String channel, String type, String payloadJson) {
        String notifyChannel = "bpm_" + channel.replace("-", "_"); // bpm_bpm_brodcast

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("channel", channel);
        envelope.put("type", type);
        envelope.put("payload", payloadJson);
        envelope.put("ts", Instant.now().toEpochMilli());

        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize notify envelope", e);
        }

        try {
            // SELECT pg_notify 는 void 반환이라 queryForObject 는 취약. 결과를 무시하는 query 사용.
            jdbc.query("SELECT pg_notify(?, ?)",
                    (java.sql.ResultSet rs) -> null,
                    notifyChannel, envelopeJson);
        } catch (Exception e) {
            // pg_notify 실패는 알림 손실이지만 도메인 트랜잭션은 살려야 한다.
            log.warn("pg_notify failed channel={}, type={}, err={}", notifyChannel, type, e.toString());
        }
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
