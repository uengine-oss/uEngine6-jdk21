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
 * <p>요청을 {@link EventInboxProviderFactory#getDefault()} 가 선택한 Provider
 * ({@code default} / {@code external}) 에 위임한다.</p>
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
        // EventInboxReceiveResult result = EventInboxProviderFactory.getDefault().receiveEvent(body);
        EventInboxReceiveResult result = provider.receiveEvent(body);
        if (result.isFailed()) {
            return ResponseEntity.badRequest().body(result.getBody());
        }
        return ResponseEntity.accepted().body(result.getBody());
    }
}
