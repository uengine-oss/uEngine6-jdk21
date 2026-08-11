package org.uengine.five.overriding;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.WorkItemAssignmentStateService;
import org.uengine.webservices.worklist.DefaultWorkList;

@Component
public class ProcessCompletionWorkitemReconciler {

    private static final List<String> ACTIVE_STATUSES = List.of("NEW", "RUNNING");

    private final WorklistRepository worklistRepository;
    private final WorkItemAssignmentStateService assignmentStateService;

    public ProcessCompletionWorkitemReconciler(
            WorklistRepository worklistRepository,
            WorkItemAssignmentStateService assignmentStateService) {
        this.worklistRepository = worklistRepository;
        this.assignmentStateService = assignmentStateService;
    }

    public int reconcile(Long instanceId) {
        if (instanceId == null) {
            return 0;
        }

        List<WorklistEntity> activeWork =
                worklistRepository.findByInstIdAndStatusIn(instanceId, ACTIVE_STATUSES);
        if (activeWork.isEmpty()) {
            assignmentStateService.synchronize(instanceId);
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
        assignmentStateService.synchronize(instanceId);
        return activeWork.size();
    }
}
