package org.uengine.five.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;

/**
 * BPM 업무/프로세스 생명주기 이벤트 서비스.
 *
 * <p>호출 위치:
 * <ul>
 *   <li>{@code JPAWorkList.addWorkItemImpl}    → {@link #onTaskAssigned}      (endpoint 있을 때)</li>
 *   <li>{@code InstanceServiceImpl.claimWorkItem} → {@link #onTaskAssigned}   (claim)</li>
 *   <li>{@code JPAWorkList.updateWorkItem}     → {@link #onTaskAssignmentChanged} (endpoint 변경 시)</li>
 *   <li>{@code JPAWorkList.completeWorkItem}   → {@link #onTaskTerminated}    (COMPLETED)</li>
 *   <li>{@code JPAWorkList.cancelWorkItem}     → {@link #onTaskTerminated}    (SKIPPED/CANCELLED)</li>
 *   <li>{@code JPAWorkList.compensateWorkItem} → {@link #onTaskTerminated}    (COMPENSATED)</li>
 *   <li>{@code JPAProcessInstance.setStatus("", Completed/Stopped)} → {@link #onProcessCompleted}</li>
 * </ul>
 *
 * <p><b>송신:</b> {@link #send(BpmLifecycleEvent)} — ESB 동기(Sync) 방식 예시 (내부 구현은 주석 처리).</p>
 */
@Service
public class BpmLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(BpmLifecycleService.class);

    // ──────────────────────────────────────────────────────────────────────
    // 1. 업무 배정 (최초)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 업무가 담당자에게 최초로 배정될 때 호출.
     * endpoint 가 null/empty 면 아직 확정되지 않은 상태이므로 호출하지 않는다.
     */
    public void onTaskAssigned(WorklistEntity wl) {
        if (wl == null || !hasEndpoint(wl)) {
            return;
        }
        BpmLifecycleEvent event = fromWorklist(wl);
        event.setEventType(BpmLifecycleEventType.TASK_ASSIGNED);

        log.debug("[BpmLifecycle] {} | taskId={} instId={} endpoint={}",
                event.getEventType(), event.getTaskId(), event.getInstanceId(),
                event.getEndpoint());

        send(event);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. 업무 배정 변경 (위임·재배정)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 이미 배정된 업무의 담당자가 변경될 때 호출.
     *
     * @param wl               변경 후 워크리스트 엔티티 (new endpoint 포함)
     * @param previousEndpoint 변경 전 endpoint
     */
    public void onTaskAssignmentChanged(WorklistEntity wl, String previousEndpoint) {
        if (wl == null) {
            return;
        }
        BpmLifecycleEvent event = fromWorklist(wl);
        event.setEventType(BpmLifecycleEventType.TASK_ASSIGNMENT_CHANGED);
        event.setPrevEndpoint(previousEndpoint);

        log.debug("[BpmLifecycle] {} | taskId={} instId={} {} → {}",
                event.getEventType(), event.getTaskId(), event.getInstanceId(),
                previousEndpoint, event.getEndpoint());

        send(event);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. 업무 종료
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 업무가 종료(완료·스킵·취소·보상 등)될 때 호출.
     */
    public void onTaskTerminated(WorklistEntity wl) {
        if (wl == null) {
            return;
        }
        BpmLifecycleEvent event = fromWorklist(wl);
        event.setEventType(BpmLifecycleEventType.TASK_TERMINATED);

        log.debug("[BpmLifecycle] {} | taskId={} instId={} endpoint={}",
                event.getEventType(), event.getTaskId(), event.getInstanceId(),
                event.getEndpoint());

        send(event);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. 메인 프로세스 인스턴스 종료
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 메인(루트) 프로세스 인스턴스가 전체 종료될 때 호출.
     * 서브프로세스({@code isSubProcess=true})는 호출하지 않는다.
     */
    public void onProcessCompleted(ProcessInstanceEntity pi) {
        if (pi == null || pi.isSubProcess()) {
            return;
        }
        BpmLifecycleEvent event = fromProcessInstance(pi);
        event.setEventType(BpmLifecycleEventType.PROCESS_COMPLETED);

        log.debug("[BpmLifecycle] {} | instId={} rootInstId={}",
                event.getEventType(), event.getInstanceId(), event.getRootInstId());

        send(event);
    }

    // ──────────────────────────────────────────────────────────────────────
    // ESB 동기 송신
    // ──────────────────────────────────────────────────────────────────────

    /**
     * ESB 동기(Sync) 통신으로 생명주기 이벤트를 송신한다.
     *
     * <p>호출 스레드가 ESB 응답을 받을 때까지 대기하는 방식이다.
     * 연동 스펙(엔드포인트·헤더·페이로드)이 확정되면 아래 예시 블록의 주석을 해제하고
     * {@code application.yml} 등에서 URL·타임아웃을 주입한다.</p>
     *
     * <pre>
     * POST {esb.lifecycle.url}
     *   Header: Content-Type: application/json
     *   Header: X-Event-Type:   {event.eventType}
     *   Header: X-Transaction-Id: {UUID}
     *   Header: X-Corr-Key:     {event.instanceId}  (선택)
     *   Body:   BpmLifecycleEvent JSON
     * </pre>
     */
    private void send(BpmLifecycleEvent event) {
        if (event == null) {
            return;
        }

        log.trace("[BpmLifecycle] send(sync) stub | {}", event);

        // ── ESB 동기 호출 예시 (연동 확정 후 주석 해제) ─────────────────────
        /*
        String esbUrl = "https://esb.example.com/bpm/lifecycle";
        // @Value("${esb.lifecycle.url}") private String esbLifecycleUrl;

        String transactionId = java.util.UUID.randomUUID().toString();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("X-Event-Type", event.getEventType());
        headers.set("X-Transaction-Id", transactionId);
        if (event.getInstanceId() != null) {
            headers.set("X-Corr-Key", String.valueOf(event.getInstanceId()));
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        String payload = mapper.writeValueAsString(event);
        org.springframework.http.HttpEntity<String> request =
                new org.springframework.http.HttpEntity<>(payload, headers);

        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                esbUrl,
                org.springframework.http.HttpMethod.POST,
                request,
                String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("[BpmLifecycle] ESB sync send failed | status={} event={}",
                    response.getStatusCode(), event);
        }
        */
    }

    // ──────────────────────────────────────────────────────────────────────
    // 내부 빌더
    // ──────────────────────────────────────────────────────────────────────

    private BpmLifecycleEvent fromWorklist(WorklistEntity wl) {
        BpmLifecycleEvent e = new BpmLifecycleEvent();
        e.setTaskId(wl.getTaskId());
        e.setInstanceId(wl.getInstId());
        e.setRootInstId(wl.getRootInstId() != null ? wl.getRootInstId() : wl.getInstId());
        e.setTracingTag(wl.getTrcTag());
        e.setEndpoint(wl.getEndpoint());
        return e;
    }

    private BpmLifecycleEvent fromProcessInstance(ProcessInstanceEntity pi) {
        BpmLifecycleEvent e = new BpmLifecycleEvent();
        e.setInstanceId(pi.getInstId());
        e.setRootInstId(pi.getRootInstId() != null ? pi.getRootInstId() : pi.getInstId());
        return e;
    }

    private static boolean hasEndpoint(WorklistEntity wl) {
        String ep = wl.getEndpoint();
        return ep != null && !ep.trim().isEmpty();
    }
}
