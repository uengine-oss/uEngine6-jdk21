package org.uengine.five.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 외부 이벤트 Inbox 통합 응답 DTO.
 *
 * <p>{@code POST /events/inbox} 의 모든 응답 케이스를 단일 객체로 표현한다.
 * 클라이언트는 {@link #status} 한 필드로 OK/Fail 분기하고, 멱등 중복 여부는 {@link #duplicate}
 * 플래그로 추가 판별한다.</p>
 *
 * <p>{@code status} 가질 수 있는 값:
 * <ul>
 *   <li>{@link #STATUS_SUCCESS} — 큐에 안전하게 보관됨 (신규 INSERT 또는 멱등 중복).</li>
 *   <li>{@link #STATUS_FAILED}  — 잘못된 payload 등으로 거부됨.</li>
 * </ul></p>
 *
 * <p>케이스별 채워지는 필드:
 * <ul>
 *   <li>신규 enqueue : status=SUCCESS, duplicate=false, eventType, corrKey, occurredAt(=신규 row.createdAt)</li>
 *   <li>멱등 중복   : status=SUCCESS, duplicate=true,  eventType, corrKey, occurredAt(=기존 row.createdAt)
 *       — (corrKey, eventType) 가 UNIQUE 라 두 키만으로 기존 row 식별 가능</li>
 *   <li>실패       : status=FAILED,  duplicate=false, reason, eventType, corrKey, occurredAt=null
 *       — inbox row 가 만들어지지 않았으므로 createdAt 없음</li>
 * </ul>
 * 비어있는 참조 필드는 {@code @JsonInclude(NON_NULL)} 로 직렬화에서 자동 제외된다.
 * {@code duplicate} 는 primitive 라 항상 노출됨 (의도된 동작 — 클라이언트가 신뢰성 있게 분기).</p>
 *
 * <p>인스턴스는 {@link #success}, {@link #duplicate}, {@link #failed} 정적 팩터리로만 생성한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventInboxResponse {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";

    private String status;

    private boolean duplicate;

    private String eventType;

    private String corrKey;

    private String reason;

    /**
     * 이벤트가 inbox 에 들어간 시각 (= BPM_EVENT_INBOX.created_at, UTC).
     * <ul>
     *   <li>SUCCESS + duplicate=false : 새로 만든 row 의 createdAt</li>
     *   <li>SUCCESS + duplicate=true  : 기존 row 의 createdAt (= 처음 큐에 들어간 시각)</li>
     *   <li>FAILED                    : null (row 자체가 없음)</li>
     * </ul>
     */
    private Instant occurredAt;

    /** 신규 INSERT 성공. {@code createdAt} 은 INSERT 된 row 의 createdAt. */
    public static EventInboxResponse success(String eventType, String corrKey, Instant createdAt) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_SUCCESS;
        r.duplicate = false;
        r.eventType = eventType;
        r.corrKey = corrKey;
        r.occurredAt = createdAt;
        return r;
    }

    /**
     * 멱등 중복으로 무시됨. (corrKey, eventType) 가 UNIQUE 라
     * 클라이언트가 두 키만으로 기존 row 를 식별할 수 있다.
     * {@code existingCreatedAt} 은 충돌한 기존 row 의 createdAt.
     */
    public static EventInboxResponse duplicate(String eventType, String corrKey, Instant existingCreatedAt) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_SUCCESS;
        r.duplicate = true;
        r.eventType = eventType;
        r.corrKey = corrKey;
        r.occurredAt = existingCreatedAt;
        return r;
    }

    /** 잘못된 payload 등 처리 실패. inbox row 가 만들어지지 않았으므로 occurredAt 은 null. */
    public static EventInboxResponse failed(String eventType, String corrKey, String reason) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_FAILED;
        r.duplicate = false;
        r.eventType = eventType;
        r.corrKey = corrKey;
        r.reason = reason;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
