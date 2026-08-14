package org.uengine.hwlife.instance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.dto.BulkAssignRequestItem;

@Service
public class BulkAssignItemService {

  private final InstanceServiceImpl instanceService;
  private final WorklistRepository worklistRepository;

  public BulkAssignItemService(
      InstanceServiceImpl instanceService,
      WorklistRepository worklistRepository) {
    this.instanceService = instanceService;
    this.worklistRepository = worklistRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void assign(BulkAssignRequestItem item, String targetEndpoint, String targetName) {
    long taskId;
    try {
      taskId = Long.parseLong(item.getFncgBpmTaskLstId().trim());
    } catch (NumberFormatException exception) {
      throw failure(BulkAssignResultCode.INVALID_TASK_ID, exception);
    }

    WorklistEntity worklist = worklistRepository.findByIdForUpdate(taskId).orElse(null);
    if (worklist == null) {
      throw failure(BulkAssignResultCode.WORKITEM_NOT_FOUND);
    }
    String requestedInstanceId = trimToNull(item.getFncgBpmPcesIntcId());
    if (requestedInstanceId != null
        && !requestedInstanceId.equals(String.valueOf(worklist.getInstId()))
        && !requestedInstanceId.equals(String.valueOf(worklist.getRootInstId()))) {
      throw failure(BulkAssignResultCode.INSTANCE_MISMATCH);
    }
    if (!"NEW".equalsIgnoreCase(trimToNull(worklist.getStatus()))) {
      throw failure(BulkAssignResultCode.WORKITEM_NOT_NEW);
    }
    if (trimToNull(worklist.getEndpoint()) != null) {
      throw failure(BulkAssignResultCode.ALREADY_ASSIGNED);
    }
    if (worklist.getDispatchOption() != 1) {
      throw failure(BulkAssignResultCode.NOT_BULK_ASSIGNABLE);
    }

    RoleMappingCommand mapping = new RoleMappingCommand();
    mapping.setEndpoint(targetEndpoint);
    mapping.setResourceName(targetName);
    try {
      instanceService.claimWorkItem(String.valueOf(taskId), mapping);
    } catch (ResponseStatusException exception) {
      throw failure(BulkAssignResultCode.CLAIM_REJECTED, exception);
    } catch (Exception exception) {
      throw failure(BulkAssignResultCode.ASSIGNMENT_FAILED, exception);
    }
  }

  private static BulkAssignItemException failure(String resultCode) {
    return new BulkAssignItemException(resultCode, null);
  }

  private static BulkAssignItemException failure(String resultCode, Throwable cause) {
    return new BulkAssignItemException(resultCode, cause);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static class BulkAssignItemException extends RuntimeException {
    private final String resultCode;

    BulkAssignItemException(String resultCode, Throwable cause) {
      super(resultCode, cause);
      this.resultCode = resultCode;
    }

    public String getResultCode() {
      return resultCode;
    }
  }
}
