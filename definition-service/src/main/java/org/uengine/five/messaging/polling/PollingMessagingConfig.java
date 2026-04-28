package org.uengine.five.messaging.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.messaging.EventPublisher;

/**
 * 폴링 전략 활성화 (definition-service). publish-only 이므로 poller/SSE/Inbox 는 등록하지
 * 않고 InboxEventPublisher 만 제공한다.
 */
@Configuration
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class PollingMessagingConfig {

    @Bean
    public EventPublisher inboxEventPublisher(EventInboxRepository repo, JdbcTemplate jdbc) {
        return new InboxEventPublisher(repo, jdbc);
    }
}
