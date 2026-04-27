package org.uengine.five.messaging;

import java.util.Collections;
import java.util.Map;

/**
 * Messaging 전략 SPI. 도메인 코드는 이 인터페이스만 참조하며, 실제 구현(Kafka/Outbox)은
 * application.yml 의 {@code uengine.messaging.mode} 값에 따라 {@code @ConditionalOnProperty}
 * 로 스위칭된다.
 *
 * <p>채널 이름은 기존 Streams 상수를 그대로 쓴다: {@code bpm-out}, {@code bpm-brodcast},
 * {@code bpm-in-0}.
 */
public interface EventPublisher {

    /**
     * 이벤트 발행.
     *
     * @param channel 채널 이름 (예: "bpm-out", "bpm-brodcast")
     * @param payload 발행할 페이로드 객체 (BusinessEvent 서브타입 또는 Map)
     * @param headers 메시지 헤더. {@code "type"} 키는 외부 이벤트 라우팅에 쓰이므로
     *                비어있지 않다면 반드시 전달되어야 한다. null 허용.
     */
    void send(String channel, Object payload, Map<String, Object> headers);

    /** 헤더 없는 간편 발행. */
    default void send(String channel, Object payload) {
        send(channel, payload, Collections.emptyMap());
    }
}
