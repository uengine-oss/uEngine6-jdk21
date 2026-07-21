package org.uengine.five.messaging;

import org.uengine.five.dto.EventInboxRequest;
import org.uengine.five.dto.EventInboxResponse;

/**
 * Event Inbox 공통 인입 서비스.
 *
 * <p>corr_key, event_name, payload 를 받아 BPM_EVENT_INBOX 에 저장한다.
 * (corr_key, event_name) 복합 UNIQUE 위반 시 멱등 성공으로 처리한다.</p>
 *
 * <p>Default/External Provider 가 요청 DTO 를 파싱한 뒤 {@link EventInboxRequest} 로
 * 정규화하여 이 서비스에 위임한다.</p>
 */
public interface EventInboxEnqueueService {

    EventInboxResponse enqueue(EventInboxRequest request);
}
