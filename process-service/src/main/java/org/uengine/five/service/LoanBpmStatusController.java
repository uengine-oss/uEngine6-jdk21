package org.uengine.five.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.LoanActiveWorkItemResponse;
import org.uengine.five.dto.LoanStatusResponse;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactional;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

@RestController
@RequestMapping("/loan-bpm")
public class LoanBpmStatusController {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorklistRepository worklistRepository;

    public LoanBpmStatusController(ProcessInstanceRepository processInstanceRepository,
                                   WorklistRepository worklistRepository) {
        this.processInstanceRepository = processInstanceRepository;
        this.worklistRepository = worklistRepository;
    }

    @GetMapping("/status/{loanProcessManagementNo}")
    @ProcessTransactional(readOnly = true)
    public LoanStatusResponse getStatus(@PathVariable String loanProcessManagementNo,
                                        HttpServletRequest request) {
        requireAuthenticated(request);

        List<ProcessInstanceEntity> instances = processInstanceRepository.findByCorrKey(loanProcessManagementNo);
        LoanStatusResponse response = new LoanStatusResponse();
        response.setLoanProcessManagementNo(loanProcessManagementNo);

        if (instances == null || instances.isEmpty()) {
            response.setProcessStatus("NO_CASE");
            return response;
        }

        List<ProcessInstanceEntity> runningInstances = findByStatus(instances, "Running");
        if (!runningInstances.isEmpty()) {
            response.setProcessStatus("RUNNING");
            response.setActiveWorkItems(findVisibleActiveWorkItems(runningInstances));
            return response;
        }

        response.setProcessStatus(hasStatus(instances, "Stopped") ? "STOPPED" : "COMPLETED");
        return response;
    }

    private void requireAuthenticated(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String userId = UserContext.getThreadLocalInstance().getUserId();
        if (authorization == null || !authorization.startsWith("Bearer ")
                || userId == null || userId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
    }

    private List<ProcessInstanceEntity> findByStatus(List<ProcessInstanceEntity> instances, String status) {
        List<ProcessInstanceEntity> result = new ArrayList<>();
        for (ProcessInstanceEntity instance : instances) {
            if (instance != null && status.equalsIgnoreCase(instance.getStatus())) {
                result.add(instance);
            }
        }
        return result;
    }

    private boolean hasStatus(List<ProcessInstanceEntity> instances, String status) {
        return !findByStatus(instances, status).isEmpty();
    }

    private List<LoanActiveWorkItemResponse> findVisibleActiveWorkItems(List<ProcessInstanceEntity> instances) {
        Set<Long> visibleTaskIds = new HashSet<>();
        List<WorklistEntity> visibleWorkItems = worklistRepository.findToDo();
        if (visibleWorkItems != null) {
            for (WorklistEntity workItem : visibleWorkItems) {
                if (workItem != null && workItem.getTaskId() != null) {
                    visibleTaskIds.add(workItem.getTaskId());
                }
            }
        }

        Map<Long, LoanActiveWorkItemResponse> result = new LinkedHashMap<>();
        for (ProcessInstanceEntity instance : instances) {
            Long rootInstId = instance.getRootInstId() != null ? instance.getRootInstId() : instance.getInstId();
            if (rootInstId == null) {
                continue;
            }
            List<WorklistEntity> activeWorkItems = worklistRepository.findCurrentWorkItemByInstId(rootInstId);
            if (activeWorkItems == null) {
                continue;
            }
            for (WorklistEntity workItem : activeWorkItems) {
                if (workItem != null && workItem.getTaskId() != null && visibleTaskIds.contains(workItem.getTaskId())) {
                    result.putIfAbsent(workItem.getTaskId(), toResponse(workItem));
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private LoanActiveWorkItemResponse toResponse(WorklistEntity workItem) {
        LoanActiveWorkItemResponse response = new LoanActiveWorkItemResponse();
        response.setTaskId(workItem.getTaskId());
        response.setInstanceId(workItem.getInstId());
        response.setTitle(workItem.getTitle());
        response.setStatus(workItem.getStatus());
        response.setRoleName(workItem.getRoleName());
        response.setGroupCd(workItem.getGroupCd());
        response.setScope(workItem.getScope());
        response.setStartDate(workItem.getStartDate());
        response.setDueDate(workItem.getDueDate());
        return response;
    }
}
