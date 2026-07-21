package org.uengine.five.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Event Inbox 공통 enqueue 결과 DTO.
 *
 * <p>{@link org.uengine.five.messaging.EventInboxEnqueueService} 가 반환하며,
 * Default/External Provider 가 각자의 {@code *Response} 로 변환한다.</p>
 *
 * <p>{@code status} 가질 수 있는 값:
 * <ul>
 *   <li>{@link #STATUS_SUCCESS} — 큐에 안전하게 보관됨 (신규 INSERT 또는 멱등 중복).</li>
 *   <li>{@link #STATUS_FAILED}  — 잘못된 payload 등으로 거부됨.</li>
 * </ul></p>
 *
 * <p>케이스별 채워지는 필드:
 * <ul>
 *   <li>신규 enqueue : status=SUCCESS, duplicate=false, eventName, corrKey, occurredAt(=신규 row.createdAt)</li>
 *   <li>멱등 중복   : status=SUCCESS, duplicate=true,  eventName, corrKey, occurredAt(=기존 row.createdAt)</li>
 *   <li>실패       : status=FAILED,  duplicate=false, reason, eventName, corrKey, occurredAt=null</li>
 * </ul>
 * 비어있는 참조 필드는 {@code @JsonInclude(NON_NULL)} 로 직렬화에서 자동 제외된다.</p>
 *
 * <p>인스턴스는 {@link #success}, {@link #duplicate}, {@link #failed} 정적 팩터리로만 생성한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventInboxResponse {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";

    private String status;

    private boolean duplicate;

    private String eventName;

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
    public static EventInboxResponse success(String eventName, String corrKey, Instant createdAt) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_SUCCESS;
        r.duplicate = false;
        r.eventName = eventName;
        r.corrKey = corrKey;
        r.occurredAt = createdAt;
        return r;
    }

    /**
     * 멱등 중복으로 무시됨. (corrKey, eventName) 가 UNIQUE 라
     * 클라이언트가 두 키만으로 기존 row 를 식별할 수 있다.
     * {@code existingCreatedAt} 은 충돌한 기존 row 의 createdAt.
     */
    public static EventInboxResponse duplicate(String eventName, String corrKey, Instant existingCreatedAt) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_SUCCESS;
        r.duplicate = true;
        r.eventName = eventName;
        r.corrKey = corrKey;
        r.occurredAt = existingCreatedAt;
        return r;
    }

    /** 잘못된 payload 등 처리 실패. inbox row 가 만들어지지 않았으므로 occurredAt 은 null. */
    public static EventInboxResponse failed(String eventName, String corrKey, String reason) {
        EventInboxResponse r = new EventInboxResponse();
        r.status = STATUS_FAILED;
        r.duplicate = false;
        r.eventName = eventName;
        r.corrKey = corrKey;
        r.reason = reason;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCorrKey() { return corrKey; }
    public void setCorrKey(String corrKey) { this.corrKey = corrKey; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
