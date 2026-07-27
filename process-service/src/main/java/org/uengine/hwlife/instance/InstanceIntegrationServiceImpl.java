package org.uengine.hwlife.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.dto.*;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequestItem;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponseItem;
import org.uengine.kernel.Activity;

/**
 * {@link InstanceIntegrationService} REST 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class InstanceIntegrationServiceImpl implements InstanceIntegrationService {

  private final InstanceServiceImpl instanceService;
  private final ProcessInstanceRepository processInstanceRepository;
  private final WorklistRepository worklistRepository;

  public InstanceIntegrationServiceImpl(
      InstanceServiceImpl instanceService,
      ProcessInstanceRepository processInstanceRepository,
      WorklistRepository worklistRepository) {
    this.instanceService = instanceService;
    this.processInstanceRepository = processInstanceRepository;
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

  @Override
  @Transactional(readOnly = true)
  public RunningWorkByCorrKeyResponse searchRunningWorkByCorrKey(
      @RequestBody RunningWorkByCorrKeyRequest request) {
    RunningWorkByCorrKeyResponse response = new RunningWorkByCorrKeyResponse();
    List<RunningWorkByCorrKeyResponseItem> resultItems = new ArrayList<>();
    response.setBswrList(resultItems);

    if (request == null || request.getBswrList() == null || request.getBswrList().isEmpty()) {
      return response;
    }

    Set<String> corrKeys = new LinkedHashSet<>();
    for (RunningWorkByCorrKeyRequestItem requestItem : request.getBswrList()) {
      String corrKey = requestItem == null ? null : trimToNull(requestItem.getLoanPcesMgmtNo());
      if (corrKey != null) {
        corrKeys.add(corrKey);
      }
    }

    Map<String, List<ProcessInstanceEntity>> instancesByCorrKey = new HashMap<>();
    Set<Long> rootInstIds = new LinkedHashSet<>();
    if (!corrKeys.isEmpty()) {
      List<ProcessInstanceEntity> runningInstances =
          processInstanceRepository.findByCorrKeyInAndStatus(corrKeys, Activity.STATUS_RUNNING);
      for (ProcessInstanceEntity processInstance : runningInstances) {
        instancesByCorrKey
            .computeIfAbsent(processInstance.getCorrKey(), ignored -> new ArrayList<>())
            .add(processInstance);
        rootInstIds.add(rootInstId(processInstance));
      }
    }

    Map<Long, List<WorklistEntity>> workItemsByRootInstId = new HashMap<>();
    if (!rootInstIds.isEmpty()) {
      List<WorklistEntity> workItems =
          worklistRepository.findCurrentWorkItemsByRootInstIds(rootInstIds);
      for (WorklistEntity workItem : workItems) {
        workItemsByRootInstId
            .computeIfAbsent(workItem.getRootInstId(), ignored -> new ArrayList<>())
            .add(workItem);
      }
    }

    for (RunningWorkByCorrKeyRequestItem requestItem : request.getBswrList()) {
      String loanPcesMgmtNo = requestItem == null ? null : trimToNull(requestItem.getLoanPcesMgmtNo());
      if (loanPcesMgmtNo == null) {
        resultItems.add(resultItem(null, null, null, "loanPcesMgmtNo is required"));
        continue;
      }

      List<ProcessInstanceEntity> runningInstances =
          instancesByCorrKey.getOrDefault(loanPcesMgmtNo, Collections.emptyList());
      if (runningInstances.isEmpty()) {
        resultItems.add(resultItem(loanPcesMgmtNo, null, null,
            "No running BPM instance found for loanPcesMgmtNo=" + loanPcesMgmtNo));
        continue;
      }

      for (ProcessInstanceEntity processInstance : runningInstances) {
        List<WorklistEntity> workItems =
            workItemsByRootInstId.getOrDefault(rootInstId(processInstance), Collections.emptyList());
        if (workItems.isEmpty()) {
          resultItems.add(resultItem(loanPcesMgmtNo, processInstance, null,
              "No active work item found for running BPM instance instId=" + processInstance.getInstId()));
          continue;
        }

        for (WorklistEntity workItem : workItems) {
          resultItems.add(resultItem(loanPcesMgmtNo, processInstance, workItem, null));
        }
      }
    }

    return response;
  }

  private static Long rootInstId(ProcessInstanceEntity processInstance) {
    return processInstance.getRootInstId() == null
        ? processInstance.getInstId()
        : processInstance.getRootInstId();
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }

  private static RunningWorkByCorrKeyResponseItem resultItem(
      String loanPcesMgmtNo,
      ProcessInstanceEntity processInstance,
      WorklistEntity workItem,
      String resultMessage) {
    RunningWorkByCorrKeyResponseItem item = new RunningWorkByCorrKeyResponseItem();
    item.setLoanPcesMgmtNo(loanPcesMgmtNo);
    item.setPrcsrsltCntn(resultMessage);

    if (processInstance != null) {
      item.setPrgsSttsNm(processInstance.getStatus());
    }
    if (workItem != null) {
      item.setFncgBpmTaskTrcgNm(workItem.getTrcTag());
      item.setFncgBpmUworSttsCntn(workItem.getStatus());
    }

    return item;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
