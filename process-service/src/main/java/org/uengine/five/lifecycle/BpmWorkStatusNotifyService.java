package org.uengine.five.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;

/**
 * BPM 현 업무 상태 알림.
 *
 * <p>담당자 변경·업무 종료·프로세스 종료 등 상태 변화를 외부에 알린다.
 * 현재는 훅·이벤트 구조만 유지하며, ESB 송신은 추후 구현한다.</p>
 */
@Service
public class BpmWorkStatusNotifyService {

    private static final Logger log = LoggerFactory.getLogger(BpmWorkStatusNotifyService.class);

    public void notifyAssigned(WorklistEntity wl, ProcessInstanceEntity pi) {
        if (wl == null) {
            return;
        }
        BpmLifecycleEvent event = new BpmLifecycleEvent();
        // event.setEventType(BpmLifecycleEvent.TASK_TERMINATED);

        // log.debug("[BpmWorkStatus] {} | taskId={} instId={} endpoint={}",
        //         event.getEventType(), event.getTaskId(), event.getInstanceId(),
        //         event.getEndpoint());

        dispatch(event);
    }

    /**
     * 담당자 변경 (위임·재배정).
     */
    public void notifyAssignmentChanged(WorklistEntity wl, ProcessInstanceEntity pi) {
        if (wl == null) {
            return;
        }
        BpmLifecycleEvent event = new BpmLifecycleEvent();
        // event.setEventType(BpmLifecycleEvent.TASK_ASSIGNMENT_CHANGED);
        // event.setPrevEndpoint(previousEndpoint);

        // log.debug("[BpmWorkStatus] {} | taskId={} instId={} {} → {}",
        //         event.getEventType(), event.getTaskId(), event.getInstanceId(),
        //         previousEndpoint, event.getEndpoint());

        dispatch(event);
    }

    /**
     * 업무 종료 (완료·스킵·취소·보상·위임 종료 등).
     */
    public void notifyTerminated(WorklistEntity wl, ProcessInstanceEntity pi) {
        if (wl == null) {
            return;
        }
        BpmLifecycleEvent event = new BpmLifecycleEvent();
        // event.setEventType(BpmLifecycleEvent.TASK_TERMINATED);

        // log.debug("[BpmWorkStatus] {} | taskId={} instId={} endpoint={}",
        //         event.getEventType(), event.getTaskId(), event.getInstanceId(),
        //         event.getEndpoint());

        dispatch(event);
    }

    /**
     * 메인(루트) 프로세스 인스턴스 전체 종료.
     */
    public void notifyProcessCompleted(ProcessInstanceEntity pi) {
        if (pi == null || pi.isSubProcess()) {
            return;
        }
        BpmLifecycleEvent event = new BpmLifecycleEvent();
        // event.setEventType(BpmLifecycleEvent.PROCESS_COMPLETED);

        // log.debug("[BpmWorkStatus] {} | instId={} rootInstId={}",
        //         event.getEventType(), event.getInstanceId(), event.getRootInstId());

        dispatch(event);
    }

    /**
     * 추후 ESB·기타 채널로 상태 알림을 송신한다.
     */
    private void dispatch(BpmLifecycleEvent event) {
        log.trace("[BpmWorkStatus] dispatch stub (not implemented) | {}", event);
    }
}
