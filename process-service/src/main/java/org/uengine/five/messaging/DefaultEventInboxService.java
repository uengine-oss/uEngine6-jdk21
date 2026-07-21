package org.uengine.five.messaging;

import org.uengine.five.ProcessServiceApplication;

/**
 * 기본(Default) Event Inbox 수신 서비스.
 *
 * <p>요청/응답 DTO:
 * {@link org.uengine.five.dto.DefaultEventInboxRequest} /
 * {@link org.uengine.five.dto.DefaultEventInboxResponse}</p>
 *
 * <p>인입 처리는 공통 {@link EventInboxEnqueueService} 에 위임한다.</p>
 *
 * <p>애플리케이션 기동 시
 * {@code EventInboxProviderFactory.register("default", DefaultEventInboxService.getDefault())} 로 등록합니다.</p>
 *
 * <p>구현: {@link DefaultEventInboxServiceImpl}.</p>
 */
public interface DefaultEventInboxService extends EventInboxProvider {

    static DefaultEventInboxService getDefault() {
        return ProcessServiceApplication.getApplicationContext().getBean(DefaultEventInboxService.class);
    }
}
