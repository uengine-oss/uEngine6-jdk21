package org.uengine.hwlife.events;

import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventInboxProvider;
import org.uengine.five.messaging.EventInboxReceiveResult;

/**
 * 외부(External) Event Inbox 수신 서비스.
 *
 * <p>{@code event-inbox.provider=external} 일 때 두 경로를 함께 사용한다.
 * <ul>
 *   <li>{@code POST /inbox} — {@link #receiveEvent}
 *       ({@link org.uengine.hwlife.events.dto.ExternalEventInboxRequest} /
 *       {@link org.uengine.hwlife.events.dto.ExternalEventInboxResponse})</li>
 *   <li>{@code POST /inbox-apvl} — {@link #receiveApvlEvent}
 *       ({@link org.uengine.hwlife.events.dto.ExternalItgtApvlInboxRequest} /
 *       {@link org.uengine.hwlife.events.dto.ExternalItgtApvlInboxResponse})</li>
 * </ul>
 * </p>
 *
 * <p>구현: {@link ExternalEventInboxServiceImpl}.</p>
 */
public interface ExternalEventInboxService extends EventInboxProvider {

    /**
     * 통합승인(ItgtApvl) Inbox 수신({@code /inbox-apvl}).
     *
     * @param requestBodyJson 요청 body JSON 문자열 (null/blank → 빈 객체로 처리)
     */
    EventInboxReceiveResult receiveApvlEvent(String requestBodyJson);

    static ExternalEventInboxService getDefault() {
        return ProcessServiceApplication.getApplicationContext().getBean(ExternalEventInboxService.class);
    }
}
