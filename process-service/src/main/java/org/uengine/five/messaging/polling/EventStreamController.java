package org.uengine.five.messaging.polling;

import java.security.Principal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 엔드포인트. 프론트가 {@code /events/stream?channel=bpm-brodcast&access_token=...} 로
 * 접속하면 PgNotifyListener 가 전달하는 이벤트를 한 방향으로 흘려보낸다.
 *
 * <p>Keycloak Bearer 토큰은 EventSource 표준상 헤더에 실을 수 없으므로 쿼리파라미터
 * {@code access_token} 으로 받고, {@link SseAccessTokenQueryFilter} 가 이를 Authorization
 * 헤더로 변환하여 기존 인증 체인에 태운다.
 */
@RestController
@RequestMapping("/events")
@ConditionalOnProperty(name = "uengine.messaging.mode", havingValue = "polling")
public class EventStreamController {

    private final PgNotifyListener listener;

    public EventStreamController(PgNotifyListener listener) {
        this.listener = listener;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(name = "channel", defaultValue = "bpm-brodcast") String channel,
            Principal principal) {

        // bpm-brodcast → bpm_bpm_brodcast (대시 → 언더스코어; Postgres LISTEN 호환)
        String notifyChannel = "bpm_" + channel.replace("-", "_");

        SseEmitter emitter = new SseEmitter(0L); // timeout 0 = 영구 유지
        String user = principal != null ? principal.getName() : null;
        return listener.register(notifyChannel, user, emitter);
    }
}
