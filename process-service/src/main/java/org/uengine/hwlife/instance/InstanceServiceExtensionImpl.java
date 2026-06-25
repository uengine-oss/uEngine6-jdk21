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
import org.uengine.five.dto.WorkItemResource;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.dto.ClaimRequest;
import org.uengine.hwlife.instance.dto.ClaimResponse;
import org.uengine.hwlife.instance.dto.DelegateRequest;
import org.uengine.hwlife.instance.dto.DelegateResponse;

/**
 * 인스턴스 명령 REST API 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceServiceExtensionImpl implements InstanceServiceExtension {

  private final InstanceServiceImpl instanceService;

  public InstanceServiceExtensionImpl(InstanceServiceImpl instanceService) {
    this.instanceService = instanceService;
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
  public void forceClaimWorkItems(@RequestBody(required = false) Map<String, Object> body) throws Exception {
    throw notImplemented("forceClaimWorkItems");
  }

  @Override
  @Transactional
  public Map<String, Object> reassignWorkItems(@RequestBody(required = false) Map<String, Object> body)
      throws Exception {
    throw notImplemented("reassignWorkItems");
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
