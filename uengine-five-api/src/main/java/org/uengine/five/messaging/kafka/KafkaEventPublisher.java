package org.uengine.five.messaging.kafka;

import java.util.Map;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.uengine.five.messaging.EventPublisher;

/**
 * Kafka 전략 구현. 기존 StreamBridge 호출 로직을 그대로 래핑하여 EventPublisher 계약을 만족.
 * process-service 와 definition-service 가 공용으로 사용.
 */
public class KafkaEventPublisher implements EventPublisher {

    private final StreamBridge streamBridge;

    public KafkaEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void send(String channel, Object payload, Map<String, Object> headers) {
        MessageBuilder<Object> builder = (MessageBuilder<Object>) MessageBuilder
                .withPayload(payload)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON);

        if (headers != null) {
            for (Map.Entry<String, Object> e : headers.entrySet()) {
                if (e.getValue() != null) {
                    builder.setHeader(e.getKey(), e.getValue());
                }
            }
        }

        streamBridge.send(channel, builder.build());
    }
}
