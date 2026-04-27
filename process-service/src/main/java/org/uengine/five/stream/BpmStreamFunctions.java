package org.uengine.five.stream;

import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * Spring Cloud Stream 4 (functional) – single Consumer for bpm-in. Kafka 모드일 때만 활성화.
 */
@Configuration
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "kafka", matchIfMissing = true)
public class BpmStreamFunctions {

    @Bean
    public Consumer<Message<String>> bpm(BpmMessageDispatcher dispatcher) {
        return dispatcher::dispatch;
    }
}
