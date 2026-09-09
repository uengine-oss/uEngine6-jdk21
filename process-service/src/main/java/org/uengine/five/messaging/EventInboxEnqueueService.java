package org.uengine.five.messaging;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;

/**
 * Event Inbox 공통 인입 서비스.
 *
 * <p>corr_key, event_name, payload 를 받아 BPM_EVENT_INBOX 에 저장한다.
 * (corr_key, event_name) 복합 UNIQUE 위반 시 멱등성 오류(실패)로 처리한다.</p>
 *
 * <p>Default/External Provider 가 요청 DTO 를 파싱한 뒤 {@link EventInboxRequest} 로
 * 정규화하여 이 서비스에 위임한다.</p>
 */
public interface EventInboxEnqueueService {

    EventInboxResponse enqueue(EventInboxRequest request);

    /**
     * Inbox INSERT 시 SEQUENCE 로 할당된 {@code BPM_EVENT_INBOX.id} 로 corrKey 를 확정한다.
     *
     * <p>같은 트랜잭션에서 id 확보 → corrKey/payload 세팅 후 커밋한다.
     * 반환 {@link EventInboxResponse#getCorrKey()} 가 채번된 업무키이다.</p>
     */
    EventInboxResponse enqueueWithCorrKeyFromId(
            String eventName,
            String payloadJson,
            Function<Long, String> corrKeyFromId,
            BiFunction<String, String, String> payloadWithCorrKey);
}
