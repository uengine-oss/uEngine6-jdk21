package org.uengine.five.messaging;

/**
 * Event Inbox 수신 Provider.
 *
 * <p>Default/External 구현체가 각자 요청 JSON 을 파싱하고 응답을 반환한다.
 * {@link EventInboxProviderFactory} 로 런타임에 구현체를 선택한다.</p>
 */
public interface EventInboxProvider {

    /** 이 Provider의 식별자. "default" 또는 "external" 등 */
    String getProviderId();

    /**
     * @param requestBodyJson 요청 body JSON 문자열 (null/blank → 빈 객체로 처리)
     */
    EventInboxReceiveResult receiveEvent(String requestBodyJson);
}
