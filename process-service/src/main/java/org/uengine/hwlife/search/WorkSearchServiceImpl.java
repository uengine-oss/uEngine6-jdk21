package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.search.dto.*;
import org.uengine.kernel.Activity;

/**
 * BPM 통합 검색 REST API 구현. Repository 연동은 추후 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class WorkSearchServiceImpl implements WorkSearchService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final MyTodoSearchRepository myTodoSearchRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final WorklistRepository worklistRepository;

  public WorkSearchServiceImpl(
      MyTodoSearchRepository myTodoSearchRepository,
      ProcessInstanceRepository processInstanceRepository,
      WorklistRepository worklistRepository) {
    this.myTodoSearchRepository = myTodoSearchRepository;
    this.processInstanceRepository = processInstanceRepository;
    this.worklistRepository = worklistRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) {
    MyTodoRequest normalizedRequest = request == null ? new MyTodoRequest() : request;
    validateSortDirection(normalizedRequest.getSortDirection());
    Long cursorTaskId = parseCursor(normalizedRequest.getCursor());
    int pageSize = normalizeSize(normalizedRequest.getSize());
    MyTodoSearchRepository.SearchResult result = myTodoSearchRepository.search(
        normalizedRequest,
        cursorTaskId,
        pageSize,
        UserContext.getThreadLocalInstance());

    MyTodoResponse response = new MyTodoResponse();
    response.setTotCont(result.totalCount());
    response.setTodolist(result.items().stream()
        .map(this::toMyTodoItem)
        .collect(Collectors.toList()));
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public MyProgressResponse searchMyProgress(@RequestBody MyProgressRequest request) {
    throw notImplemented("searchMyProgress");
  }

  @Override
  @Transactional(readOnly = true)
  public OrgRunningResponse searchOrgRunning(@RequestBody OrgRunningRequest request) {
    throw notImplemented("searchOrgRunning");
  }

  @Override
  @Transactional(readOnly = true)
  public OrgCompletedResponse searchOrgCompleted(@RequestBody OrgCompletedRequest request) {
    throw notImplemented("searchOrgCompleted");
  }

  @Override
  @Transactional(readOnly = true)
  public BulkAssignSearchResponse searchBulkAssign(@RequestBody BulkAssignSearchRequest request) {
    throw notImplemented("searchBulkAssign");
  }

  @Override
  @Transactional(readOnly = true)
  public WorklistByInstIdResponse searchWorklistByInstId(@RequestBody WorklistByInstIdRequest request) {
    throw notImplemented("searchWorklistByInstId");
  }

  @Override
  @Transactional(readOnly = true)
  public RunningWorkByCorrKeyResponse searchRunningWorkByCorrKey(@RequestBody RunningWorkByCorrKeyRequest request) {
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
        resultItems.add(runningWorkResult(null, null, null, "loanPcesMgmtNo is required"));
        continue;
      }

      List<ProcessInstanceEntity> runningInstances =
          instancesByCorrKey.getOrDefault(loanPcesMgmtNo, Collections.emptyList());
      if (runningInstances.isEmpty()) {
        resultItems.add(runningWorkResult(loanPcesMgmtNo, null, null,
            "No running BPM instance found for loanPcesMgmtNo=" + loanPcesMgmtNo));
        continue;
      }

      for (ProcessInstanceEntity processInstance : runningInstances) {
        List<WorklistEntity> workItems =
            workItemsByRootInstId.getOrDefault(rootInstId(processInstance), Collections.emptyList());
        if (workItems.isEmpty()) {
          resultItems.add(runningWorkResult(loanPcesMgmtNo, processInstance, null,
              "No active work item found for running BPM instance instId=" + processInstance.getInstId()));
          continue;
        }

        for (WorklistEntity workItem : workItems) {
          resultItems.add(runningWorkResult(loanPcesMgmtNo, processInstance, workItem, null));
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

  private static RunningWorkByCorrKeyResponseItem runningWorkResult(
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

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }

  private MyTodoItem toMyTodoItem(WorklistEntity worklist) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    MyTodoItem item = new MyTodoItem();

    item.setBswrClsfCode(instance == null ? null : instance.getBswrClsfCode());
    item.setCustId(instance == null ? null : instance.getCustId());
    item.setFncgBswrDvsnCode(instance == null ? null : instance.getFncgBswrDvsnCode());
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setUworStarDttm(worklist.getStartDate());
    item.setUworNm(worklist.getTitle());
    item.setLoanPcesNm(firstNonBlank(worklist.getDefName(), instance == null ? null : instance.getDefName()));
    item.setReptHndrEmnb(instance == null ? null : instance.getInitEp());
    item.setReptHndrFncgOrgnCode(firstNonBlank(worklist.getAssignGroup(), worklist.getScope()));
    item.setPrcdHndrEmnb(worklist.getPrevEndpoint());
    item.setPrcdHndrFncgOrgnCode(worklist.getPrevGroupCd());
    item.setFncgBpmUworSttsCntn(worklist.getStatus());
    item.setStarDttm(instance == null ? null : instance.getStartedDate());
    item.setBefrHndrEmnb(worklist.getPrevEndpoint());
    item.setBefrFncgOrgnCode(worklist.getPrevGroupCd());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrNm(worklist.getResName());
    item.setHndrOrgnCode(firstNonBlank(worklist.getAssignGroup(), worklist.getScope()));
    item.setScrnUrlAddr(worklist.getTool());
    item.setFncgBpmTaskLstId(worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(worklist.getInstId() == null ? null : String.valueOf(worklist.getInstId()));
    return item;
  }

  private static Long parseCursor(String cursor) {
    String value = trimToNull(cursor);
    if (value == null) {
      return null;
    }
    try {
      long taskId = Long.parseLong(value);
      if (taskId <= 0) {
        throw new NumberFormatException("cursor must be positive");
      }
      return taskId;
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "cursor must be a positive fncgBpmTaskLstId",
          exception);
    }
  }

  private static int normalizeSize(Integer size) {
    if (size == null) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
  }

  private static void validateSortDirection(String sortDirection) {
    String value = trimToNull(sortDirection);
    if (value != null
        && !"ASC".equalsIgnoreCase(value)
        && !"DESC".equalsIgnoreCase(value)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "sortDirection must be ASC or DESC");
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      String trimmed = trimToNull(value);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
