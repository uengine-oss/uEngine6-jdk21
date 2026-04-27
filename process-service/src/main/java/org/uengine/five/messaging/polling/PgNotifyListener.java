package org.uengine.five.messaging.polling;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Postgres LISTEN/NOTIFY 를 전용 connection 에서 받아 SSE emitter 들에 전달.
 *
 * <p>pgjdbc 는 비동기 notification push 를 지원하지 않으므로 전용 connection 을 하나 잡고
 * 백그라운드 스레드에서 {@code getNotifications(200ms)} 로 블로킹 폴링한다. 알림이 오면
 * 즉시 깨어나 CPU 는 거의 소비하지 않는다. 연결이 끊기면 자동 재연결 + LISTEN 재실행.
 */
public class PgNotifyListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PgNotifyListener.class);

    /** notify channel -> 구독 중인 emitter 들. */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final DataSource dataSource;
    private final Set<String> listenChannels;

    private volatile Thread worker;
    private volatile boolean running = true;

    public PgNotifyListener(DataSource dataSource, Set<String> listenChannels) {
        this.dataSource = dataSource;
        this.listenChannels = listenChannels;
    }

    public void start() {
        worker = new Thread(this::loop, "pg-notify-listener");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 구독 등록. 'userId' 는 현재 미사용 (향후 participants 필터링에 사용).
     */
    public SseEmitter register(String notifyChannel, String userId, SseEmitter emitter) {
        emitters.computeIfAbsent(notifyChannel, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(notifyChannel, emitter));
        emitter.onTimeout(() -> remove(notifyChannel, emitter));
        emitter.onError(t -> remove(notifyChannel, emitter));
        return emitter;
    }

    private void remove(String ch, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(ch);
        if (set != null) set.remove(emitter);
    }

    private void loop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                // LISTEN 문 실행 (채널별)
                try (Statement st = conn.createStatement()) {
                    for (String ch : listenChannels) {
                        st.execute("LISTEN \"" + ch + "\"");
                    }
                }
                PGConnection pgconn = conn.unwrap(PGConnection.class);
                log.info("[pg-notify] listening on {}", listenChannels);

                while (running) {
                    PGNotification[] notifs = pgconn.getNotifications(200);
                    if (notifs == null) continue;
                    for (PGNotification n : notifs) {
                        dispatch(n.getName(), n.getParameter());
                    }
                }
            } catch (SQLException e) {
                if (!running) return;
                log.warn("[pg-notify] connection issue, reconnecting in 2s: {}", e.toString());
                sleep(2000);
            } catch (Exception e) {
                if (!running) return;
                log.error("[pg-notify] unexpected", e);
                sleep(2000);
            }
        }
    }

    private void dispatch(String channel, String payload) {
        Set<SseEmitter> set = emitters.get(channel);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (Exception e) {
                remove(channel, emitter);
            }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
