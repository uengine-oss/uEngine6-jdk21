package org.uengine.five.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;

/**
 * Event Inbox 공통 인입 구현.
 *
 * <p>NOTE: 의도적으로 {@code @Transactional} 을 두지 않는다.
 * Postgres 는 트랜잭션 안에서 UNIQUE 위반이 나면 트랜잭션이 abort 되어
 * 이후 같은 트랜잭션에서 SELECT 를 못 하므로, 중복 행 조회를 위해 트랜잭션을 걸지 않는다.
 * {@code repo.save(...)} / {@code repo.findFirstBy...} 는 각자 자체 트랜잭션을 가진다.</p>
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
    public EventInboxResponse enqueue(EventInboxRequest request) {
        String eventName = request != null ? request.getEventName() : null;
        String corrKey = request != null ? request.getCorrKey() : null;
        String payloadJson = request != null ? request.getPayloadJson() : null;
        String normalizedPayload = payloadJson != null ? payloadJson : "{}";

        EventInbox ev = new EventInbox();
        ev.setEventName(eventName);
        ev.setPayload(normalizedPayload);
        ev.setCorrKey(corrKey);

        try {
            repo.save(ev);
        } catch (DataIntegrityViolationException dup) {
            EventInbox existing = findExistingInboxForDuplicate(corrKey, eventName);
            Long existingId = existing != null ? existing.getId() : null;
            log.info("[inbox] duplicate (corrKey={}, eventName={}, existingId={}), treated as idempotent success",
                    corrKey, eventName, existingId);
            return EventInboxResponse.duplicate(
                    eventName,
                    corrKey,
                    existing != null ? existing.getCreatedAt() : null);
        }

        return EventInboxResponse.success(eventName, corrKey, ev.getCreatedAt());
    }

    private EventInbox findExistingInboxForDuplicate(String corrKey, String eventName) {
        if (corrKey == null || eventName == null) {
            return null;
        }
        return repo.findFirstByCorrKeyAndEventName(corrKey, eventName).orElse(null);
    }
}
