package org.uengine.hwlife.instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.*;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.five.spring.SecurityAwareServletFilter;
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
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
    }
    if (request.getBswrList() == null || request.getBswrList().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bswrList is required");
    }

    boolean unclaim = isUnclaim(request.getDvsnVal());
    String actorEndpoint = resolveClaimActorEndpoint(request.getHndrEmnb());
    UserContext.getThreadLocalInstance().setUserId(actorEndpoint);

    ClaimResponse response = new ClaimResponse();
    List<ClaimResponseItem> failList = new ArrayList<>();
    Set<String> seenTaskIds = new HashSet<>();
    int successCount = 0;

    for (ClaimRequestItem item : request.getBswrList()) {
      String taskId = item == null ? null : trimToNull(item.getFncgBpmTaskLstId());
      if (taskId == null) {
        addClaimFailure(failList, item, "fncgBpmTaskLstId is required");
        continue;
      }
      if (!seenTaskIds.add(taskId)) {
        addClaimFailure(failList, item, "duplicate fncgBpmTaskLstId in request");
        continue;
      }

      try {
        WorklistEntity worklist = worklistRepository.findByIdForUpdate(Long.parseLong(taskId)).orElse(null);
        if (worklist == null) {
          addClaimFailure(failList, item, "No such work item where taskId=" + taskId);
          continue;
        }
        if (isAlreadyInRequestedClaimState(worklist, actorEndpoint, unclaim)) {
          successCount++;
          continue;
        }

        String validationError = validateClaimRequest(worklist, item, actorEndpoint, unclaim);
        if (validationError != null) {
          addClaimFailure(failList, item, validationError);
          continue;
        }

        RoleMappingCommand roleMapping = null;
        if (!unclaim) {
          roleMapping = new RoleMappingCommand();
          roleMapping.setEndpoint(actorEndpoint);
        }
        instanceService.claimWorkItem(taskId, roleMapping);
        successCount++;
      } catch (NumberFormatException e) {
        addClaimFailure(failList, item, "fncgBpmTaskLstId must be numeric");
      } catch (ResponseStatusException e) {
        addClaimFailure(failList, item, e.getReason() == null ? e.getMessage() : e.getReason());
      } catch (Exception e) {
        addClaimFailure(failList, item, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      }
    }

    response.setPrcsRsltCodeNm(failList.isEmpty() ? "SUCCESS" : "FAILED");
    response.setSucsCont(successCount);
    response.setFailCont(failList.size());
    response.setFailList(failList);
    return response;
  }

  private boolean isUnclaim(String dvsnVal) {
    String normalized = trimToNull(dvsnVal);
    if ("0".equals(normalized)) return false;
    if ("1".equals(normalized)) return true;
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dvsnVal must be 0 (claim) or 1 (unclaim)");
  }

  private String resolveClaimActorEndpoint(String requestedEndpoint) {
    String authenticatedActor = resolveAuthenticatedActorEndpoint();
    if (authenticatedActor == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authenticated actor is required for claim");
    }
    String requested = trimToNull(requestedEndpoint);
    if (requested != null && !requested.equals(authenticatedActor)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "hndrEmnb must match the authenticated actor");
    }
    return authenticatedActor;
  }

  private String resolveAuthenticatedActorEndpoint() {
    String actorEndpoint = trimToNull(UserContext.getThreadLocalInstance().getUserId());
    if (actorEndpoint == null) {
      actorEndpoint = trimToNull(SecurityAwareServletFilter.getUserId());
    }
    return actorEndpoint;
  }

  private String validateClaimRequest(
      WorklistEntity worklist,
      ClaimRequestItem requestItem,
      String actorEndpoint,
      boolean unclaim) {
    String requestedInstanceId = requestItem == null ? null : trimToNull(requestItem.getFncgBpmPcesIntcId());
    if (requestedInstanceId != null
        && !requestedInstanceId.equals(String.valueOf(worklist.getInstId()))
        && !requestedInstanceId.equals(String.valueOf(worklist.getRootInstId()))) {
      return "fncgBpmPcesIntcId does not match the work item instance";
    }

    if (!"NEW".equals(trimToNull(worklist.getStatus()))) {
      return "Task is not claimable because status=" + worklist.getStatus();
    }

    String currentEndpoint = trimToNull(worklist.getEndpoint());
    if (unclaim) {
      if (currentEndpoint == null || !currentEndpoint.equals(actorEndpoint)) {
        return "Only the current claimant can unclaim this task";
      }
      return null;
    }

    if (currentEndpoint != null) {
      return currentEndpoint.equals(actorEndpoint)
          ? "Task is already claimed by the login user"
          : "Task already claimed by another user. endpoint=" + currentEndpoint;
    }
    if (worklist.getDispatchOption() != 1) {
      return "Task is not a racing claim target. dispatchOption=" + worklist.getDispatchOption();
    }
    if (!isClaimableByCurrentUser(worklist)) {
      return "No permission to claim this racing task. groupCd=" + worklist.getGroupCd();
    }
    return null;
  }

  private boolean isAlreadyInRequestedClaimState(
      WorklistEntity worklist,
      String actorEndpoint,
      boolean unclaim) {
    String currentEndpoint = trimToNull(worklist.getEndpoint());
    if (unclaim) {
      return currentEndpoint == null;
    }
    return actorEndpoint != null && actorEndpoint.equals(currentEndpoint);
  }

  private boolean isClaimableByCurrentUser(WorklistEntity worklist) {
    List<String> groups = UserContext.getThreadLocalInstance().getGroups();
    String groupCd = trimToNull(worklist.getGroupCd());
    return groupCd != null && containsNormalized(groups, groupCd);
  }

  private static boolean containsNormalized(Collection<String> values, String expected) {
    String normalizedExpected = trimToNull(expected);
    if (values == null || normalizedExpected == null) return false;
    for (String value : values) {
      if (normalizedExpected.equals(trimToNull(value))) return true;
    }
    return false;
  }

  private static void addClaimFailure(
      List<ClaimResponseItem> failList,
      ClaimRequestItem source,
      String reason) {
    ClaimResponseItem failure = new ClaimResponseItem();
    if (source != null) {
      failure.setFncgBpmTaskLstId(source.getFncgBpmTaskLstId());
      failure.setFncgBpmPcesIntcId(source.getFncgBpmPcesIntcId());
    }
    failure.setPrcsRsltCntn(reason);
    failList.add(failure);
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Override
  @Transactional
  public DelegateResponse delegateWorkItems(@RequestBody DelegateRequest request)
      throws Exception {
    throw notImplemented("delegateWorkItems");
  }

  @Override
  @Transactional
  public BulkAssignResponse assignBulk(@RequestBody BulkAssignRequest request) throws Exception {
    throw notImplemented("assignBulk");
  }

  @Override
  @Transactional
  public ReassignResponse reassignWorkItems(@RequestBody ReassignRequest request)
      throws Exception {
    throw notImplemented("reassignWorkItems");
  }

  @Override
  @Transactional
  public TaskSkipResponse skipWorklist(@RequestBody TaskSkipRequest request) throws Exception {
    throw notImplemented("skipWorklist");
  }

  @Override
  @Transactional
  public TaskReturnResponse returnToPrevious(@RequestBody TaskReturnRequest request) throws Exception {
    throw notImplemented("returnToPrevious");
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

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }
}
