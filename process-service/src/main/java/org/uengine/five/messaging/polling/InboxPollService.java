package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;

@Service
public class InboxPollService {

    private static final Logger log = LoggerFactory.getLogger(InboxPollService.class);

    private final EventInboxRepository repo;
    private final InboxDispatchService dispatchService;
    private final int batchSize;
    private final int maxTryCnt;

    public InboxPollService(EventInboxRepository repo,
                            InboxDispatchService dispatchService,
                            @Value("${uengine.messaging.polling.batch-size:50}") int batchSize,
                            @Value("${uengine.messaging.polling.max-try-cnt:3}") int maxTryCnt) {
        this.repo = repo;
        this.dispatchService = dispatchService;
        this.batchSize = batchSize;
        this.maxTryCnt = maxTryCnt;
    }

    @Transactional
    public void runBatch() {
        List<EventInbox> batch = repo.lockUnprocessed(batchSize);
        if (batch.isEmpty()) return;

        if (log.isDebugEnabled()) {
            log.debug("[inbox-poll] picked {} events", batch.size());
        }

        Instant now = Instant.now();
        for (EventInbox ev : batch) {
            ev.setTryCnt(ev.getTryCnt() + 1);
            try {
                dispatchService.dispatchInNewTx(ev);
                ev.setProcessedAt(now);
                ev.setLastError(null);
                ev.setStatus("SUCCESS");
            } catch (Exception e) {
                String msg = truncate(e.toString() + " | " + rootCauseMessage(e), 2000);
                ev.setLastError(msg);
                if (ev.getTryCnt() >= maxTryCnt) {
                    ev.setStatus("FAILED");
                    ev.setProcessedAt(now);
                    log.error("[inbox-poll] id={} type={} reached max try cnt ({}) - dead-letter",
                            ev.getId(), ev.getEventName(), maxTryCnt, e);
                } else {
                    ev.setStatus("PENDING");
                    log.warn("[inbox-poll] id={} type={} try {}/{} failed, will retry",
                            ev.getId(), ev.getEventName(), ev.getTryCnt(), maxTryCnt, e);
                }
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getName() + ": " + cur.getMessage();
    }
}
