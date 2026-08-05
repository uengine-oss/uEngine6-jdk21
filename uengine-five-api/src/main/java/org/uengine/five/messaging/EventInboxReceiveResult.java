package org.uengine.five.messaging;

/**
 * EventInbox Provider 처리 결과.
 *
 * <p>HTTP 상태는 항상 200 이다. (ESB Adapter 는 HTTP 200 만 정상으로 인정)
 * 업무 상세는 body(ESB header·payload) 에 담는다.</p>
 *
 * <ul>
 *   <li>{@link #success} — 성공 ({@code prcsRsltDvsnCode=0}, 업무 실패 상세도 payload 에 포함 가능)</li>
 *   <li>{@link #failed} — 실패 (시스템)</li>
 * </ul>
 */
public class EventInboxReceiveResult {

    private final Object body;
    private final boolean failed;

    public EventInboxReceiveResult(Object body, boolean failed) {
        this.body = body;
        this.failed = failed;
    }

    /** 성공. */
    public static EventInboxReceiveResult success(Object body) {
        return new EventInboxReceiveResult(body, false);
    }

    /** 실패 (시스템). */
    public static EventInboxReceiveResult failed(Object body) {
        return new EventInboxReceiveResult(body, true);
    }

    public Object getBody() {
        return body;
    }

    public boolean isFailed() {
        return failed;
    }
}
