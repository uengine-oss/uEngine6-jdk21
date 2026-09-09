package org.uengine.five.messaging.polling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.messaging.DefaultEventInboxService;
import org.uengine.five.messaging.EventInboxProvider;
import org.uengine.five.messaging.EventInboxReceiveResult;
import org.uengine.hwlife.events.ExternalEventInboxService;

/**
 * Event Inbox HTTP 진입점.
 *
 * <p>Provider 모드는 {@code default} / {@code external} 둘뿐이다.
 * <ul>
 *   <li>{@code default} — {@code POST /inbox}
 *       ({@link DefaultEventInboxService#getDefault()})</li>
 *   <li>{@code external} — {@code POST /inbox}({@link ExternalEventInboxService#receiveEvent})
 *       + {@code POST /inbox-apvl}({@link ExternalEventInboxService#receiveApvlEvent})</li>
 * </ul>
 * HTTP 는 항상 200 — 처리 결과는 {@link EventInboxReceiveResult} body 에 담는다.
 * (ESB Adapter 는 HTTP 200 만 정상으로 인정)</p>
 */
@RestController
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class EventInboxController {

    private final String resolvedProviderId;

    public EventInboxController(@Value("${event-inbox.provider:default}") String providerId) {
        String envOverride = System.getenv("EVENT_INBOX_PROVIDER");
        this.resolvedProviderId =
                (envOverride != null && !envOverride.isBlank()) ? envOverride : providerId;
    }

    @PostMapping("/inbox")
    public ResponseEntity<Object> receiveEvent(@RequestBody(required = false) String body) {
        EventInboxReceiveResult result = resolveInboxProvider().receiveEvent(body);
        return ResponseEntity.ok(result.getBody());
    }

    /** External 모드 전용 — 통합승인 Inbox. */
    @PostMapping("/inbox-apvl")
    public ResponseEntity<Object> receiveApvlEvent(@RequestBody(required = false) String body) {
        requireExternal();
        EventInboxReceiveResult result = ExternalEventInboxService.getDefault().receiveApvlEvent(body);
        return ResponseEntity.ok(result.getBody());
    }

    private EventInboxProvider resolveInboxProvider() {
        if (isExternal()) {
            return ExternalEventInboxService.getDefault();
        }
        return DefaultEventInboxService.getDefault();
    }

    private void requireExternal() {
        if (!isExternal()) {
            throw new IllegalStateException(
                    "/inbox-apvl requires event-inbox.provider=external");
        }
    }

    private boolean isExternal() {
        return "external".equalsIgnoreCase(resolvedProviderId);
    }
}
