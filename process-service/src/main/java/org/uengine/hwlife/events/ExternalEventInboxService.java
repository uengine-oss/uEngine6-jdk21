package org.uengine.hwlife.events;

import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventInboxProvider;

/**
 * 외부(External) Event Inbox 수신 서비스.
 *
 * <p>ESB {@code { header, payload }} 전문을 수신한다.
 * payload/응답 payload DTO:
 * {@link org.uengine.hwlife.events.dto.ExternalEventInboxRequest} /
 * {@link org.uengine.hwlife.events.dto.ExternalEventInboxResponse}
 * (봉투: {@link org.uengine.hwlife.esbclient.dto.EsbRequest} /
 * {@link org.uengine.hwlife.esbclient.dto.EsbResponse})</p>
 *
 * <p>인입 처리는 공통 {@link org.uengine.five.messaging.EventInboxEnqueueService} 에 위임한다.</p>
 *
 * <p>{@code event-inbox.provider=external} 로 선택합니다.</p>
 *
 * <p>구현: {@link ExternalEventInboxServiceImpl}.</p>
 */
public interface ExternalEventInboxService extends EventInboxProvider {

    static ExternalEventInboxService getDefault() {
        return ProcessServiceApplication.getApplicationContext().getBean(ExternalEventInboxService.class);
    }
}
