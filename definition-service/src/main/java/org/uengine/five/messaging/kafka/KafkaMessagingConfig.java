package org.uengine.five.messaging.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.uengine.five.messaging.EventPublisher;

/**
 * Kafka 전략 활성화 (definition-service).  definition-service 는 publish-only 라 Consumer
 * Bean 은 등록하지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaMessagingConfig {

    @Bean
    public EventPublisher kafkaEventPublisher(StreamBridge streamBridge) {
        return new KafkaEventPublisher(streamBridge);
    }
}
