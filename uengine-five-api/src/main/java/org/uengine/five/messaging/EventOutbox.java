package org.uengine.five.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Transactional Outbox row. 폴링 모드에서 엔진 내부 이벤트(bpm-out / bpm-in-0) 를
 * DB 테이블에 쌓아두고, OutboxEventPoller 가 꺼내 BpmMessageDispatcher 로 전달한다.
 *
 * <p>프론트용 브로드캐스트(bpm-brodcast) 는 이 테이블에 저장하지 않고
 * OutboxEventPublisher 가 pg_notify 만 호출한다.
 */
@Entity
@Table(name = "BPM_EVENT_OUTBOX", indexes = {
        @Index(name = "idx_outbox_unprocessed", columnList = "channel, processed_at")
})
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "CHANNEL", nullable = false, length = 64)
    private String channel;

    @Column(name = "EVENT_TYPE", length = 128)
    private String eventType;

    /**
     * 원본 payload JSON 문자열. Postgres 기본 TEXT 컬럼에 저장 (jsonb 는 Hibernate 기본 String
     * 바인딩과 호환되지 않아 INSERT 가 실패한다). 이 outbox 는 단순 key-by-id 저장소 용도라
     * JSONB 함수가 불필요.
     */
    @Column(name = "PAYLOAD", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "HEADERS", columnDefinition = "TEXT")
    private String headers;

    /** 외부 발행자가 지정한 멱등성 키. 동일 값 재인입 시 DB 유니크 제약으로 차단. */
    @Column(name = "EVENT_ID", length = 64, unique = true)
    private String eventId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "PROCESSED_AT")
    private Instant processedAt;

    @Column(name = "CONSUMER_ID", length = 64)
    private String consumerId;

    /** 처리 시도 횟수. dispatch 실패 시 증가하며 재시도 가시화 용도. */
    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts = 0;

    /** 마지막 dispatch 실패 메시지. dead-letter 진단용. */
    @Column(name = "LAST_ERROR", columnDefinition = "TEXT")
    private String lastError;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
