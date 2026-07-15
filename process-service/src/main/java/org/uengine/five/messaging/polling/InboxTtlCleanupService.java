package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventInboxRepository;

@Service
public class InboxTtlCleanupService {

    private static final Logger log = LoggerFactory.getLogger(InboxTtlCleanupService.class);

    private final EventInboxRepository repo;
    private final int ttlHours;

    public InboxTtlCleanupService(EventInboxRepository repo,
                                  @Value("${uengine.messaging.polling.inbox-ttl-hours:24}") int ttlHours) {
        this.repo = repo;
        this.ttlHours = ttlHours;
    }

    @Transactional
    public void cleanup() {
        Instant olderThan = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        int deleted = repo.deleteProcessedBefore(olderThan);
        if (deleted > 0) {
            log.info("[inbox-ttl] deleted {} processed rows older than {}h ({})", deleted, ttlHours, olderThan);
        }
    }
}
