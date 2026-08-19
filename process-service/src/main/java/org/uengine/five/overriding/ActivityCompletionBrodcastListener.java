package org.uengine.five.overriding;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.five.notification.WorkItemNotificationService;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.kernel.Activity;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.IActivityCompletionListener;
import org.uengine.kernel.ProcessInstance;
import org.uengine.webservices.worklist.DefaultWorkList;

/**
 * HumanActivity 가 완료될 때마다
 * <ol>
 *   <li>{@code bpm-brodcast} 로 TASK_COMPLETED 를 발행하고,</li>
 *   <li>해당 workitem 의 worklist 행을 COMPLETED 로 닫는다.</li>
 * </ol>
 *
 * <p>(2)가 필요한 이유: 외부 애플리케이션이 Kafka 이벤트로 workitem 을 완료시키는 경로
 * ({@code AsyncEventListener} → {@code ReceiveActivity.fireReceived} → {@code fireComplete})
 * 는 {@code JPAWorkList.completeWorkItem()} 을 거치지 않는다. 그래서 그대로 두면 worklist 행이
 * 계속 NEW 로 남아 {@code /worklist/search/findToDo} 같은 조회가 이미 끝난 업무까지 돌려준다.
 * 외부 앱이 BPM 의 worklist API 로 "내게 배정된 할 일"을 가져가는 pull 방식 연동에서는
 * 이 정리가 없으면 큐가 비워지지 않는다.
 */
@Component
public class ActivityCompletionBrodcastListener implements IActivityCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityCompletionBrodcastListener.class);
    private static final List<String> ACTIVE_STATUSES = List.of("NEW", "RUNNING", "DRAFT");

    @Autowired
    EventPublisher eventPublisher;

    @Autowired(required = false)
    WorklistRepository worklistRepository;

    @Autowired(required = false)
    WorkItemNotificationService workItemNotificationService;

    @Override
    public void onActivityCompleted(ProcessInstance instance, Activity activity) throws Exception {
        if (!(activity instanceof HumanActivity))
            return;

        HumanActivity humanActivity = (HumanActivity) activity;

        String[] taskIds = null;
        try {
            taskIds = humanActivity.getTaskIds(instance);
        } catch (Exception ignored) {
            // optional
        }

        closeWorkItems(taskIds);

        Map<String, Object> taskEvent = new HashMap<>();
        taskEvent.put("eventType", "TASK_COMPLETED");
        taskEvent.put("instanceId", instance != null ? instance.getInstanceId() : null);
        taskEvent.put("tracingTag", activity.getTracingTag());
        taskEvent.put("activityName", activity.getName());
        taskEvent.put("taskIds", taskIds);

        eventPublisher.send("bpm-brodcast", taskEvent, Map.of("type", "TASK_COMPLETED"));
    }

    /** 완료된 활동의 worklist 행을 닫는다. 실패해도 프로세스 진행을 막지 않는다. */
    private void closeWorkItems(String[] taskIds) {
        if (worklistRepository == null || taskIds == null || taskIds.length == 0) {
            return;
        }
        List<WorklistEntity> closed = new ArrayList<>();
        Date now = new Date();

        for (String rawTaskId : taskIds) {
            if (rawTaskId == null || rawTaskId.isBlank()) {
                continue;
            }
            try {
                Long taskId = Long.valueOf(rawTaskId.trim());
                worklistRepository.findById(taskId).ifPresent(workItem -> {
                    if (!ACTIVE_STATUSES.contains(workItem.getStatus())) {
                        return;
                    }
                    workItem.setStatus(DefaultWorkList.WORKITEM_STATUS_COMPLETED);
                    if (workItem.getEndDate() == null) {
                        workItem.setEndDate(now);
                    }
                    closed.add(workItem);
                });
            } catch (NumberFormatException e) {
                log.debug("worklist 정리 건너뜀: taskId={}", rawTaskId);
            }
        }

        if (closed.isEmpty()) {
            return;
        }
        worklistRepository.saveAll(closed);

        if (workItemNotificationService != null) {
            for (WorklistEntity workItem : closed) {
                try {
                    workItemNotificationService.onTaskTerminated(workItem);
                } catch (Exception ignored) {
                    // 알림 정리 실패가 프로세스 진행을 막지 않게 한다.
                }
            }
        }
    }
}
