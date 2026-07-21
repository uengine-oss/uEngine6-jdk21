package org.uengine.five.messaging.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.five.messaging.EventInboxProviderFactory;
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

    @PostMapping("/inbox")
    public ResponseEntity<Object> receiveEvent(@RequestBody(required = false) String body) {
        EventInboxReceiveResult result = EventInboxProviderFactory.getDefault().receiveEvent(body);
        if (result.isFailed()) {
            return ResponseEntity.badRequest().body(result.getBody());
        }
        return ResponseEntity.accepted().body(result.getBody());
    }
}
