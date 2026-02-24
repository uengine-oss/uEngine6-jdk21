package org.uengine.five.stream;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.uengine.five.EventListener;
import org.uengine.five.service.AsyncEventListener;

/**
 * Spring Cloud Stream 4 (functional) – single Consumer for bpm-in.
 */
@Configuration
public class BpmStreamFunctions {

    @Bean
    public Consumer<Message<String>> bpm(
            AsyncEventListener asyncEventListener,
            EventListener eventListener) {
        BpmMessageDispatcher dispatcher = new BpmMessageDispatcher(asyncEventListener, eventListener);
        return dispatcher::dispatch;
    }
}
