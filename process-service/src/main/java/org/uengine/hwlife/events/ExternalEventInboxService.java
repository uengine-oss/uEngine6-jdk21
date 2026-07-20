package org.uengine.hwlife.events;

import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventInboxProvider;

/**
 * 외부(커스텀) Event Inbox 수신 서비스.
 *
 * <p>요청/응답 DTO 는 {@code hwlife.events.dto} 패키지 형식을 사용한다.
 * 인입 처리는 코어 {@link org.uengine.five.messaging.EventInboxEnqueueService} 에 위임한다.</p>
 *
 * <p>애플리케이션 기동 시
 * {@code EventInboxProviderFactory.register("external", ExternalEventInboxService.getDefault())} 로 등록하고,
 * {@code event-inbox.provider=external} 로 선택합니다.</p>
 *
 * <p>구현: {@link ExternalEventInboxServiceImpl}.</p>
 */
public interface ExternalEventInboxService extends EventInboxProvider {

    static ExternalEventInboxService getDefault() {
        return ProcessServiceApplication.getApplicationContext().getBean(ExternalEventInboxService.class);
    }
}
