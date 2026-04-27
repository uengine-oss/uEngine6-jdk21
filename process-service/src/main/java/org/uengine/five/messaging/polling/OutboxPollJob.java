package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.messaging.EventOutbox;
import org.uengine.five.messaging.EventOutboxRepository;
import org.uengine.five.messaging.TypedJsonObjectMapperFactory;
import org.uengine.five.stream.BpmMessageDispatcher;
import org.uengine.kernel.GlobalContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Quartz 기반 Outbox 폴링 잡. 고정 간격으로 BPM_EVENT_OUTBOX 의 미처리 row 를 꺼내
 * 기존 BpmMessageDispatcher 로 dispatch 한다.
 *
 * <p>동작 원리는 Spring @Scheduled 버전과 동일하나 트리거를 Quartz 가 관리한다.
 * 같은 Scheduler 를 BPMN TimerEvent 와 공유하므로 별도 Scheduler 인스턴스 불필요.
 *
 * <p>Quartz 는 이 Job 클래스를 no-arg 생성자로 매번 새로 만드므로 @Autowired 가 직접 동작하지
 * 않는다. {@link #execute} 에서 GlobalContext 로 Spring 빈을 한 번 더 가져와 위임하는 패턴은
 * {@code TimerEventJob} 과 동일.
 */
@Component
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class OutboxPollJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollJob.class);

    private static final List<String> INTERNAL_CHANNELS = List.of("bpm-out", "bpm-in-0");

    @Autowired
    private EventOutboxRepository repo;

    @Autowired
    private BpmMessageDispatcher dispatcher;

    @Value("${uengine.messaging.polling.consumer-id:process-service}")
    private String consumerId;

    @Value("${uengine.messaging.polling.batch-size:50}")
    private int batchSize;

    private final ObjectMapper objectMapper = TypedJsonObjectMapperFactory.create();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Quartz 가 만든 인스턴스가 아니라 Spring 컨테이너의 빈을 가져와 호출 (의존성 주입된 인스턴스).
            GlobalContext.getComponent(OutboxPollJob.class).runBatch();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    /**
     * 한 틱 분량의 outbox 처리. 각 row 마다 dispatch 결과(성공/실패)와 무관하게
     * {@code processed_at} 을 항상 채워 무한 재시도를 차단한다.
     *
     * <ul>
     *   <li>성공: {@code consumer_id} 에 자기 식별자, {@code last_error=null}</li>
     *   <li>실패: {@code consumer_id="dead-letter"}, {@code last_error} 에 예외 메시지</li>
     * </ul>
     *
     * <p>dispatch 의 부작용(예: 새 인스턴스 생성)이 실패 후 일부 커밋되더라도, 같은 row 가
     * 다시 발동되어 부작용이 누적되는 일은 없다. 운영자는 dead-letter row 를 SQL 로 확인 후
     * 수동 재처리(원인 수정 + processed_at NULL 로 되돌리기)할 수 있다.
     */
    @Transactional
    public void runBatch() {
        List<EventOutbox> batch = repo.lockUnprocessed(INTERNAL_CHANNELS, batchSize);
        if (batch.isEmpty()) return;

        if (log.isDebugEnabled()) {
            log.debug("[outbox-poll] picked {} events", batch.size());
        }

        Instant now = Instant.now();
        for (EventOutbox ev : batch) {
            ev.setAttempts(ev.getAttempts() + 1);
            ev.setProcessedAt(now);    // 성공/실패 무관하게 마킹 → 재시도 방지
            try {
                Message<String> msg = rebuildMessage(ev);
                dispatcher.dispatch(msg);
                ev.setConsumerId(consumerId);
                ev.setLastError(null);
            } catch (Exception e) {
                log.error("[outbox-poll] dispatch failed id={} channel={} type={} → dead-letter",
                          ev.getId(), ev.getChannel(), ev.getEventType(), e);
                ev.setConsumerId("dead-letter");
                ev.setLastError(truncate(e.toString() + " | " + rootCauseMessage(e), 2000));
                // 예외를 다시 던지지 않음 → outer tx 정상 커밋 → processed_at 확정
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getName() + ": " + cur.getMessage();
    }

    private Message<String> rebuildMessage(EventOutbox ev) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(ev.getPayload());

        Map<String, Object> headers = parseHeaders(ev.getHeaders());
        if (headers != null) {
            for (Map.Entry<String, Object> e : headers.entrySet()) {
                if (e.getValue() != null) builder.setHeader(e.getKey(), e.getValue());
            }
        }
        if (ev.getEventType() != null && (headers == null || headers.get("type") == null)) {
            builder.setHeader("type", ev.getEventType());
        }
        return builder.build();
    }

    private Map<String, Object> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) return null;
        try {
            return objectMapper.readValue(headersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[outbox-poll] failed to parse headers: {}", headersJson);
            return null;
        }
    }
}
