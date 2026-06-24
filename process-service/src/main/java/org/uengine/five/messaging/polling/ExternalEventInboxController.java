package org.uengine.five.messaging.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class ExternalEventInboxController {

    private final ExternalEventInboxService eventInboxService;

    public ExternalEventInboxController(ExternalEventInboxService eventInboxService) {
        this.eventInboxService = eventInboxService;
    }

    @PostMapping({ "/events/inbox", "/inbox" })
    public ResponseEntity<?> receive(
            @RequestHeader(name = "X-Event-Type", required = false) String defaultType,
            @RequestHeader(name = "X-Corr-Key", required = false) String defaultCorrKey,
            @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.accepted().body(eventInboxService.receive(defaultType, defaultCorrKey, body));
    }
}