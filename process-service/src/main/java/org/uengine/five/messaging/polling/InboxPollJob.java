package org.uengine.five.messaging.polling;

import java.time.Instant;
import java.util.List;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.messaging.EventInbox;
import org.uengine.five.messaging.EventInboxRepository;
import org.uengine.five.stream.BpmMessageDispatcher;
import org.uengine.kernel.GlobalContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Quartz 기반 Inbox 폴링 잡. 고정 간격으로 BPM_EVENT_INBOX 의 미처리 row 를 꺼내
 * 기존 BpmMessageDispatcher 로 dispatch 한다.
 *
 * <p>처리 정책:
 * <ul>
 *   <li>성공: processed_at 채움, last_error=null</li>
 *   <li>실패 (try_cnt < max): processed_at 비움 → 다음 틱에 재시도. dispatch 의 부작용
 *       (인스턴스 생성 등) 은 REQUIRES_NEW 로 격리되어 롤백됨</li>
 *   <li>실패 (try_cnt >= max): processed_at 채움 + last_error 기록 → dead-letter, 더 이상
 *       재시도 안 함</li>
 * </ul>
 *
 * <p>운영자는 dead-letter row (processed_at IS NOT NULL AND last_error IS NOT NULL) 를
 * SQL 로 확인 후 수동 재처리 가능.
 */
@Component
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class InboxPollJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(InboxPollJob.class);

    @Autowired
    private EventInboxRepository repo;

    @Autowired
    private BpmMessageDispatcher dispatcher;

    @Value("${uengine.messaging.polling.batch-size:50}")
    private int batchSize;

    @Value("${uengine.messaging.polling.max-try-cnt:3}")
    private int maxTryCnt;

    private final ObjectMapper objectMapper = ProcessServiceApplication.createTypedJsonObjectMapper();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            GlobalContext.getComponent(InboxPollService.class).runBatch();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    /**
     * 한 틱 분량의 inbox 처리. 외부 트랜잭션은 row 의 lock 유지 + try_cnt /
     * processed_at / last_error 갱신만 담당. dispatch 자체는 별도 REQUIRES_NEW 트랜잭션에서
     * 격리되어, 실패 시 부작용(인스턴스 생성 등)도 함께 롤백된다.
     */
    @Transactional
    public void runBatch() {
        if (GlobalContext.class != null) {
            GlobalContext.getComponent(InboxPollService.class).runBatch();
            return;
        }
        List<EventInbox> batch = repo.lockUnprocessed(batchSize);
        if (batch.isEmpty()) return;

        if (log.isDebugEnabled()) {
            log.debug("[inbox-poll] picked {} events", batch.size());
        }

        Instant now = Instant.now();
        InboxPollJob self = GlobalContext.getComponent(InboxPollJob.class);

        for (EventInbox ev : batch) {
            ev.setTryCnt(ev.getTryCnt() + 1);
            try {
                self.dispatchInNewTx(ev);             // REQUIRES_NEW 로 격리
                ev.setProcessedAt(now);
                ev.setLastError(null);
                ev.setStatus("SUCCESS");
            } catch (Exception e) {
                String msg = truncate(e.toString() + " | " + rootCauseMessage(e), 2000);
                ev.setLastError(msg);
                if (ev.getTryCnt() >= maxTryCnt) {
                    ev.setStatus("FAILED");
                    ev.setProcessedAt(now);            // 한도 도달 → dead-letter
                    log.error("[inbox-poll] id={} type={} reached max try cnt ({}) → dead-letter",
                              ev.getId(), ev.getEventName(), maxTryCnt, e);
                } else {
                    ev.setStatus("PENDING");
                    log.warn("[inbox-poll] id={} type={} try {}/{} failed, will retry",
                             ev.getId(), ev.getEventName(), ev.getTryCnt(), maxTryCnt, e);
                    // processed_at 비워둠 → 다음 틱에 재시도
                }
            }
        }
    }

    /**
     * dispatch 만 별도 트랜잭션으로 실행. 실패 시 이 안에서 일어난 모든 DB 변경
     * (예: 인스턴스 생성, worklist 추가) 이 롤백된다 → 재시도 시 부작용 누적 방지.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchInNewTx(EventInbox ev) {
        Message<String> msg = rebuildMessage(ev);
        dispatcher.dispatch(msg);
    }

    /**
     * inbox row 를 dispatcher 가 받는 Message 로 재구성. event_name 컬럼만으로 type 헤더 셋팅.
     */
    private Message<String> rebuildMessage(EventInbox ev) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(dispatchPayload(ev.getPayload()));
        if (ev.getEventName() != null) {
            builder.setHeader("type", ev.getEventName());
        }
        if (ev.getCorrKey() != null) {
            // payload 에 EventMapping.correlationKey 매칭 필드가 없을 때 fallback 으로 사용됨
            builder.setHeader("corrKey", ev.getCorrKey());
        }
        return builder.build();
    }

    private String dispatchPayload(String storedPayload) {
        if (storedPayload == null || storedPayload.isBlank()) {
            return "{}";
        }
        try {
            JsonNode root = objectMapper.readTree(storedPayload);
            if (isEventRequestWrapper(root)) {
                JsonNode payload = root.get("payload");
                return payload == null || payload.isNull() ? storedPayload : objectMapper.writeValueAsString(payload);
            }
        } catch (Exception ignored) {
            return storedPayload;
        }
        return storedPayload;
    }

    private boolean isEventRequestWrapper(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        return root.has("eventName") || root.has("eventname") || root.has("eventNm") || root.has("evntNm")
                || root.has("corrKey") || root.has("corrkey") || root.has("loanPcesMgmtNo")
                || root.has("prcrRsltCodeNm") || root.has("prcsRsltCodeNm") || root.has("prcsRsltCntn")
                || root.size() == 1;
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
}
