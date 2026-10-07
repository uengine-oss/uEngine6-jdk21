package org.uengine.five.messaging;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;

/**
 * Event Inbox 공통 인입 구현.
 *
 */
@Service
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class EventInboxEnqueueServiceImpl implements EventInboxEnqueueService {

    private static final Logger log = LoggerFactory.getLogger(EventInboxEnqueueServiceImpl.class);

    private final EventInboxRepository repo;

    public EventInboxEnqueueServiceImpl(EventInboxRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public EventInboxResponse enqueue(EventInboxRequest request) {
        String eventName = request != null ? request.getEventName() : null;
        String corrKey = request != null ? request.getCorrKey() : null;
        String payloadJson = request != null ? request.getPayloadJson() : null;
        String normalizedPayload = payloadJson != null ? payloadJson : "{}";

        EventInbox existing = corrKey != null && eventName != null
                ? repo.findFirstByCorrKeyAndEventNameOrderByIdDesc(corrKey, eventName).orElse(null)
                : null;
        if (existing != null && "FAILED".equals(existing.getStatus())
                && !"중복".equals(existing.getLastError())) {
            existing.setPayload(normalizedPayload);
            existing.setProcessedAt(null);
            existing.setTryCnt(0);
            existing.setLastError(null);
            existing.setStatus("PENDING");
            repo.save(existing);
            log.info("[inbox] resubmitted failed request (corrKey={}, eventName={}, id={})",
                    corrKey, eventName, existing.getId());
            return EventInboxResponse.success(eventName, corrKey, existing.getCreatedAt());
        }

        EventInbox ev = new EventInbox();
        ev.setEventName(eventName);
        ev.setPayload(normalizedPayload);
        ev.setCorrKey(corrKey);

        repo.save(ev);

        return EventInboxResponse.success(eventName, corrKey, ev.getCreatedAt());
    }

    @Override
    @Transactional
    public EventInboxResponse enqueueWithCorrKeyFromId(
            String eventName,
            String payloadJson,
            Function<Long, String> corrKeyFromId,
            BiFunction<String, String, String> payloadWithCorrKey) {
        String normalizedPayload = payloadJson != null ? payloadJson : "{}";

        EventInbox ev = new EventInbox();
        ev.setEventName(eventName);
        ev.setPayload(normalizedPayload);
        // SEQUENCE 로 id 할당 (커밋 전이라 폴러에 안 보임)
        repo.save(ev);

        String corrKey = corrKeyFromId.apply(ev.getId());
        String finalPayload = payloadWithCorrKey.apply(normalizedPayload, corrKey);
        ev.setCorrKey(corrKey);
        ev.setPayload(finalPayload != null ? finalPayload : normalizedPayload);

        log.info("[inbox] enqueued with id-based corrKey (corrKey={}, eventName={}, id={})",
                corrKey, eventName, ev.getId());
        return EventInboxResponse.success(eventName, corrKey, ev.getCreatedAt());
    }

}
