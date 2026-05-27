package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.five.messaging.TypedJsonObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 폴링 전략 발행자.
 *
 * <ul>
 *   <li>{@code bpm-brodcast} (프론트 실시간 알림): DB 저장하지 않고 {@code SELECT pg_notify(...)}
 *       만 호출. LISTEN 중인 PgNotifyListener 가 받아 SSE 로 푸시.</li>
 *   <li>그 외 채널 (bpm-out, bpm-in-0): {@code BPM_EVENT_INBOX} INSERT. InboxPollJob 가
 *       꺼내 BpmMessageDispatcher 로 전달. 채널 자체는 DB 컬럼이 아니라 라우팅 결정용
 *       메서드 파라미터로만 사용.</li>
 * </ul>
 *
 * <p>Kafka 모드에서는 이 클래스가 Bean 으로 등록되지 않고 KafkaEventPublisher 가 사용된다.
 */
public class InboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InboxEventPublisher.class);

    private static final String BRODCAST_CHANNEL = "bpm-brodcast";

    private final EventInboxRepository repo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = TypedJsonObjectMapperFactory.create();

    public InboxEventPublisher(EventInboxRepository repo, JdbcTemplate jdbc) {
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

        // 엔진 내부 / 외부 인입: inbox 저장. (Inbox 단일 채널 가정)
        EventInbox ev = new EventInbox();
        ev.setEventName(type);
        ev.setPayload(payloadJson);
        ev.setCorrKey(headers != null && headers.get("corrKey") != null ? headers.get("corrKey").toString() : null);
        repo.save(ev);
    }

    /**
     * 브로드캐스트: pg_notify 직접 호출. 트랜잭션 COMMIT 시점에 구독자에게 배달된다.
     */
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
            jdbc.query("SELECT pg_notify(?, ?)",
                    (java.sql.ResultSet rs) -> null,
                    notifyChannel, envelopeJson);
        } catch (Exception e) {
            // pg_notify 실패는 알림 손실이지만 도메인 트랜잭션은 살려야 한다.
            log.warn("pg_notify failed channel={}, type={}, err={}", notifyChannel, type, e.toString());
        }
    }
}
