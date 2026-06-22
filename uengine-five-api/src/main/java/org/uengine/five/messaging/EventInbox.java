package org.uengine.five.messaging;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * Polling 모드 외부 이벤트 인입 큐. Inbox 채널 (bpm-in-0) 로 들어오는 이벤트를 보관하고,
 * {@code InboxPollJob} 이 주기적으로 꺼내 {@code BpmMessageDispatcher} 로 전달한다.
 *
 * <p>최소 컬럼 구성:
 * <ul>
 *   <li>{@code event_name}: dispatcher 가 EventMapping 매칭에 사용</li>
 *   <li>{@code payload}: 이벤트 본문 JSON</li>
 *   <li>{@code corr_key}: 비즈니스 식별자. {@code (corr_key, event_name)} 복합 UNIQUE 로 동일
 *       트랜잭션의 동일 이벤트 재전송 멱등 + 같은 트랜잭션의 다른 이벤트 시퀀스 허용</li>
 *   <li>{@code created_at} / {@code processed_at}: 인입/처리 시각</li>
 *   <li>{@code try_cnt}: 총 시도 횟수 (1 = 첫 시도)</li>
 *   <li>{@code last_error}: 실패 시 메시지 (NULL 이면 정상, 값 있으면 dead-letter)</li>
 * </ul>
 *
 * <p>유니크 제약:
 * <pre>
 *   같은 (corr_key='X-1', event_name='START_CREDIT_RATING') 재전송 → 차단 (멱등)
 *   같은 corr_key='X-1' 의 다른 event_name='LOAN_APPROVED' → 허용 (시퀀스 이벤트)
 *   corr_key=NULL 인 이벤트 (엔진 내부 등) → UNIQUE 적용 안 됨, 자유 INSERT
 * </pre>
 */
@Entity
@Table(name = "BPM_EVENT_INBOX",
    indexes = @Index(name = "idx_inbox_unprocessed", columnList = "processed_at"),
    uniqueConstraints = @UniqueConstraint(name = "uk_inbox_corr_event", columnNames = { "corr_key", "event_name" })
)
@SequenceGenerator(
    name = "event_inbox_seq_gen",
    sequenceName = "SEQ_BPM_EVENT_INBOX",
    allocationSize = 1
)
public class EventInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_inbox_seq_gen")
    private Long id;

    private String corrKey;

    private String eventName;

    private String payload;

    private Instant createdAt = Instant.now();

    private Instant processedAt;

    /** 총 시도 횟수 (1 = 첫 시도, 2 이상 = 재시도). 모니터링/진단용. */
    @Column(name = "TRY_CNT", nullable = false)
    private int tryCnt = 0;

    /** 마지막 dispatch 실패 메시지. 값이 있으면 dead-letter 로 간주. */
    @Column(name = "LAST_ERROR", columnDefinition = "TEXT")
    private String lastError;

    /**
     * 처리 상태. processed_at / last_error 로 추론하던 상태를 운영자가 바로 조회할 수 있게 둔다.
     * PENDING: 미처리 또는 재시도 대기, SUCCESS: 처리 성공, FAILED: dead-letter.
     */
    @Column(name = "STATUS", nullable = false)
    private String status = "PENDING";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public int getTryCnt() { return tryCnt; }
    public void setTryCnt(int tryCnt) { this.tryCnt = tryCnt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
