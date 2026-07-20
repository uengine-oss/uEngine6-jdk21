package org.uengine.five.messaging;

import org.uengine.five.dto.EventInboxResponse;

/**
 * 외부 이벤트 Inbox 인입 코어 서비스.
 *
 * <p>corr_key, event_name, payload 를 받아 BPM_EVENT_INBOX 에 저장한다.
 * (corr_key, event_name) 복합 UNIQUE 위반 시 멱등 성공으로 처리한다.</p>
 */
public interface EventInboxEnqueueService {

    /**
     * @param eventName   event_name 컬럼 값
     * @param corrKey     corr_key 컬럼 값 (비즈니스 ID, 멱등성 키 겸용)
     * @param payloadJson payload 컬럼 값 (JSON 문자열)
     */
    EventInboxResponse enqueue(String eventName, String corrKey, String payloadJson);
}
