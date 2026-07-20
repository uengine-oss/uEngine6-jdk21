package org.uengine.five.messaging;

import org.uengine.five.ProcessServiceApplication;

/**
 * 코어 Event Inbox 수신 서비스.
 *
 * <p>요청 형식:
 * <pre>
 * {
 *   "eventName": "...",
 *   "corrKey":   "...",
 *   "payload":   { ... }
 * }
 * </pre>
 *
 * <p>애플리케이션 기동 시
 * {@code EventInboxProviderFactory.register("default", EventInboxService.getDefault())} 로 등록합니다.</p>
 *
 * <p>구현: {@link EventInboxServiceImpl}.</p>
 */
public interface EventInboxService extends EventInboxProvider {

    static EventInboxService getDefault() {
        return ProcessServiceApplication.getApplicationContext().getBean(EventInboxService.class);
    }
}
