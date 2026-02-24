package org.uengine.five.stream;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.events.*;
import org.uengine.five.service.AsyncEventListener;
import org.uengine.five.EventListener;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dispatches incoming bpm-in messages to AsyncEventListener and EventListener.
 * Replaces @StreamListener dispatching for Spring Cloud Stream 4 (functional).
 */
public class BpmMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BpmMessageDispatcher.class);

    private final AsyncEventListener asyncEventListener;
    private final EventListener eventListener;
    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    public BpmMessageDispatcher(AsyncEventListener asyncEventListener, EventListener eventListener) {
        this.asyncEventListener = asyncEventListener;
        this.eventListener = eventListener;
    }

    /** Kafka 헤더 값이 byte[]로 오면 UTF-8 문자열로 변환, 아니면 toString(). */
    private static String typeHeaderAsString(Object typeHeader) {
        if (typeHeader == null) return null;
        if (typeHeader instanceof byte[]) {
            return new String((byte[]) typeHeader, StandardCharsets.UTF_8);
        }
        return typeHeader.toString();
    }

    public void dispatch(Message<String> message) {
        String payload = message.getPayload();
        Object typeHeaderRaw = message.getHeaders().get("type");
        String typeHeader = typeHeaderAsString(typeHeaderRaw);

        // 진단: Kafka에서 메시지가 들어오면 여기서 한 번만 로그 (수신 여부 확인용)
        if (log.isDebugEnabled()) {
            log.debug("[BPM] message received, headers={}, payloadLength={}", message.getHeaders(), payload != null ? payload.length() : 0);
        }
        log.info("[BPM] message received from bpm-topic, typeHeader={}, dispatching.", typeHeader);

        // 1) 모든 메시지에 대해 whatever 로깅
        asyncEventListener.whatever(payload);

        // 2) type 헤더가 있으면 외부 이벤트로 wheneverEvent 처리
        if (typeHeader != null && !typeHeader.isEmpty()) {
            asyncEventListener.wheneverEvent(payload, typeHeader);
        } else if (log.isWarnEnabled()) {
            log.warn("[BPM] no 'type' header - external event path skipped. All headers: {}", message.getHeaders().keySet());
        }

        // 3) Typed JSON으로 역직렬화 후 EventListener 핸들러로 분기
        try {
            BusinessEvent event = objectMapper.readValue(payload, BusinessEvent.class);
            if (event == null) return;

            if (event instanceof ActivityDone) {
                eventListener.handleDone((ActivityDone) event);
            } else if (event instanceof ActivityFailed) {
                eventListener.handleFailed((ActivityFailed) event);
            } else if (event instanceof ActivityQueued) {
                eventListener.handleQueued((ActivityQueued) event);
            } else if (event instanceof DefinitionDeployed) {
                eventListener.handleDeployed((DefinitionDeployed) event);
            }
        } catch (Exception ignored) {
            // Typed JSON이 아니거나 다른 포맷이면 무시 (외부 이벤트 등)
        }
    }
}
