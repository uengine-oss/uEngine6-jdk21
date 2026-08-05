package org.uengine.five.messaging.polling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.messaging.EventInboxProvider;
import org.uengine.five.messaging.EventInboxReceiveResult;

/**
 * Event Inbox HTTP 진입점.
 *
 * <p>요청을 선택된 Provider({@code default} / {@code external}) 에 위임한다.
 * HTTP 는 항상 200 — 처리 결과는 {@link EventInboxReceiveResult#getOutcome()} 및 body 에 담는다.
 * (ESB Adapter 는 HTTP 200 만 정상으로 인정)</p>
 */
@RestController
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class EventInboxController {

    private final EventInboxProvider provider;

    public EventInboxController(
            ApplicationContext ctx,
            @Value("${event-inbox.provider:default}") String providerId) {
        String envOverride = System.getenv("EVENT_INBOX_PROVIDER");
        String resolvedId = (envOverride != null && !envOverride.isBlank()) ? envOverride : providerId;

        this.provider = ctx.getBeanProvider(EventInboxProvider.class)
                .stream()
                .filter(p -> resolvedId.equalsIgnoreCase(p.getProviderId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No EventInboxProvider bean found for provider '" + resolvedId + "'"));
    }

    @PostMapping("/inbox")
    public ResponseEntity<Object> receiveEvent(@RequestBody(required = false) String body) {
        EventInboxReceiveResult result = provider.receiveEvent(body);
        return ResponseEntity.ok(result.getBody());
    }
}
