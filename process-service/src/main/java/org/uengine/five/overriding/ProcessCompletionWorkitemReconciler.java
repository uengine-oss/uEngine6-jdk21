package org.uengine.five.overriding;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.notification.WorkItemNotificationService;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.webservices.worklist.DefaultWorkList;

@Component
public class ProcessCompletionWorkitemReconciler {

    private static final List<String> ACTIVE_STATUSES = List.of("NEW", "RUNNING");

    private final WorklistRepository worklistRepository;

    /**
     * 이 클래스는 JPAWorkList.completeWorkItem 을 거치지 않고 상태만 직접 바꾸므로
     * BpmLifecycleService 훅이 타지 않는다. 헤더 알림이 끝난 업무에 남지 않도록 여기서 직접 정리한다.
     */
    @Autowired(required = false)
    WorkItemNotificationService workItemNotificationService;

    public ProcessCompletionWorkitemReconciler(WorklistRepository worklistRepository) {
        this.worklistRepository = worklistRepository;
    }

    public int reconcile(Long instanceId) {
        if (instanceId == null) {
            return 0;
        }

        List<WorklistEntity> activeWork =
                worklistRepository.findByInstIdAndStatusIn(instanceId, ACTIVE_STATUSES);
        if (activeWork.isEmpty()) {
            return 0;
        }

        Date completedAt = new Date();
        for (WorklistEntity workItem : activeWork) {
            workItem.setStatus(DefaultWorkList.WORKITEM_STATUS_COMPLETED);
            if (workItem.getEndDate() == null) {
                workItem.setEndDate(completedAt);
            }
        }
        worklistRepository.saveAll(activeWork);

        if (workItemNotificationService != null) {
            for (WorklistEntity workItem : activeWork) {
                try {
                    workItemNotificationService.onTaskTerminated(workItem);
                } catch (Exception ignore) {
                    // 알림 정리 실패가 인스턴스 정리를 막지 않게 한다.
                }
            }
        }

        return activeWork.size();
    }
}
