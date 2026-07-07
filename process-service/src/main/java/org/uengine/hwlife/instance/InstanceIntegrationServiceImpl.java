package org.uengine.hwlife.instance;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.*;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.dto.*;

/**
 * {@link InstanceIntegrationService} REST 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceIntegrationServiceImpl implements InstanceIntegrationService {

  private final InstanceServiceImpl instanceService;
  private final WorklistRepository worklistRepository;

  public InstanceIntegrationServiceImpl(
      InstanceServiceImpl instanceService,
      WorklistRepository worklistRepository) {
    this.instanceService = instanceService;
    this.worklistRepository = worklistRepository;
  }

  @Override
  @Transactional
  public ClaimResponse claimWorkItems(@RequestBody ClaimRequest request) throws Exception {
    throw notImplemented("claimWorkItems");
  }

  @Override
  @Transactional
  public DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request)
      throws Exception {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
    }
    if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskIds is required");
    }

    DelegateResponse result = new DelegateResponse();
    result.setTotal(request.getTaskIds().size());

    boolean delegateOnlyForWorkitem = Boolean.TRUE.equals(request.getDelegateOnlyForWorkitem());
    Set<String> processed = new LinkedHashSet<>();

    for (String taskId : request.getTaskIds()) {
      if (taskId == null || taskId.trim().isEmpty()) {
        result.addFailure(taskId, "taskId is required");
        continue;
      }

      String normalizedTaskId = taskId.trim();
      if (!processed.add(normalizedTaskId)) {
        result.addFailure(normalizedTaskId, "Duplicated taskId");
        continue;
      }

      try {
        WorkItemResource workItem = instanceService.delegateWorkItem(
            normalizedTaskId,
            request.getDelegatedRoleMapping(),
            delegateOnlyForWorkitem);
        result.addSuccess(normalizedTaskId, workItem);
      } catch (Exception e) {
        result.addFailure(normalizedTaskId, resolveFailureReason(e));
      }
    }

    return result;
  }

  @Override
  @Transactional
  public void assignBulk(@RequestBody(required = false) Map<String, Object> body) throws Exception {
    throw notImplemented("assignBulk");
  }

  @Override
  @Transactional
  public Map<String, Object> reassignWorkItems(@RequestBody(required = false) Map<String, Object> body)
      throws Exception {
    throw notImplemented("reassignWorkItems");
  }

  @Override
  @Transactional(rollbackFor = { Exception.class })
  public TaskSkipResponse skipWorklist(@RequestBody TaskSkipRequest request) throws Exception {
    TaskSkipCommand command = new TaskSkipCommand();
    command.setReason(request.getReason());
    TaskSkipResult engine = instanceService.skipWorkItem(request.getTaskId(), command);
    return TaskSkipResponse.from(engine);
  }

  @Override
  @Transactional(rollbackFor = { Exception.class })
  public TaskReturnResponse returnToPrevious(@RequestBody TaskReturnRequest request) throws Exception {
    TaskReturnCommand command = new TaskReturnCommand();
    command.setTaskId(request.getTargetTaskId());
    command.setTracingTag(request.getTracingTag());
    command.setExecScope(request.getExecScope());
    command.setReason(request.getReason());
    TaskReturnResult engine = instanceService.returnWorkItem(request.getTaskId(), command);
    return TaskReturnResponse.from(engine);
  }

  @Override
  @Transactional(rollbackFor = { Exception.class })
  public TaskJumpResponse jumpToForward(@RequestBody TaskJumpRequest request) throws Exception {
    WorklistEntity worklist = worklistRepository.findById(Long.parseLong(request.getTaskId())).orElse(null);
    if (worklist == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "No such work item where taskId = " + request.getTaskId());
    }
    InstanceResource instance = instanceService.backToHere(
        String.valueOf(worklist.getInstId()), request.getTargetTracingTag());
    return TaskJumpResponse.from(instance, request);
  }

  @Override
  @Transactional
  public InstanceSyncResponse syncInstances(@RequestBody InstanceSyncRequest request) throws Exception {
    return new InstanceSyncResponse();
  }

  private static String resolveFailureReason(Exception e) {
    Throwable cursor = e;
    while (cursor != null) {
      if (cursor instanceof ResponseStatusException) {
        ResponseStatusException responseStatusException = (ResponseStatusException) cursor;
        String reason = responseStatusException.getReason();
        return reason != null ? reason : responseStatusException.getMessage();
      }
      cursor = cursor.getCause();
    }
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
