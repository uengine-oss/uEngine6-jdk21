package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventOutboxRepository;
import org.uengine.kernel.GlobalContext;

/**
 * 처리 완료된 outbox row 의 TTL 청소 Quartz 잡. 기본 매일 새벽 3시 실행.
 *
 * <p>{@code bpm-brodcast} 채널은 outbox 에 저장되지 않으므로 대상은 내부 이벤트
 * (bpm-out / bpm-in-0) 의 processed 행만.
 */
@Component
@DisallowConcurrentExecution
public class OutboxTtlCleanupQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(OutboxTtlCleanupQuartzJob.class);

    @Autowired
    private EventOutboxRepository repo;

    @Value("${uengine.messaging.polling.outbox-ttl-hours:24}")
    private int ttlHours;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            GlobalContext.getComponent(OutboxTtlCleanupQuartzJob.class).cleanup();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    @Transactional
    public void cleanup() {
        Instant olderThan = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        int deleted = repo.deleteProcessedBefore(olderThan);
        if (deleted > 0) {
            log.info("[outbox-ttl] deleted {} processed rows older than {}h ({})", deleted, ttlHours, olderThan);
        }
    }
}
