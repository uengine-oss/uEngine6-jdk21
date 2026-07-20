package org.uengine.five.messaging;

/**
 * EventInbox Provider 처리 결과.
 *
 * <p>Provider 구현체마다 응답 DTO 가 다를 수 있어, HTTP 레이어는 {@link #failed} 만으로
 * 상태코드를 결정하고 {@link #body} 를 그대로 직렬화한다.</p>
 */
public class EventInboxReceiveResult {

    private final Object body;
    private final boolean failed;

    public EventInboxReceiveResult(Object body, boolean failed) {
        this.body = body;
        this.failed = failed;
    }

    public static EventInboxReceiveResult success(Object body) {
        return new EventInboxReceiveResult(body, false);
    }

    public static EventInboxReceiveResult failure(Object body) {
        return new EventInboxReceiveResult(body, true);
    }

    public Object getBody() { return body; }
    public boolean isFailed() { return failed; }
}
