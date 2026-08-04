package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.search.dto.*;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;


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
  private final OrgRunningSearchRepository orgRunningSearchRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final WorklistRepository worklistRepository;

  public WorkSearchServiceImpl(
      MyTodoSearchRepository myTodoSearchRepository,
      OrgRunningSearchRepository orgRunningSearchRepository,
      ProcessInstanceRepository processInstanceRepository,
      WorklistRepository worklistRepository) {
    this.myTodoSearchRepository = myTodoSearchRepository;
    this.orgRunningSearchRepository = orgRunningSearchRepository;
    this.processInstanceRepository = processInstanceRepository;
    this.worklistRepository = worklistRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) {
    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String emnb = trimToNull(header != null ? header.getEmnb() : null);
    String belnOrgnCode = trimToNull(header != null ? header.getBelnOrgnCode() : null);
    if (emnb == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "header.emnb is required");
    }

    MyTodoRequest normalizedRequest = requireMyTodoRequest(request);
    Long cursorId = parseNextKey(normalizedRequest.getNextKey());
    int pageSize = normalizePageSize(normalizedRequest.getPageSize());
    MyTodoSearchRepository.SearchResult result = myTodoSearchRepository.search(
        normalizedRequest,
        cursorId,
        pageSize,
        emnb,
        belnOrgnCode);

    MyTodoResponse response = new MyTodoResponse();
    response.setTotCont(result.totalCount());
    response.setNextKey(result.nextKey());
    response.setTodoList(result.items().stream()
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
    OrgRunningRequest normalizedRequest = request == null ? new OrgRunningRequest() : request;
    Long cursorId = parseNextKey(normalizedRequest.getNextKey());
    int pageSize = normalizePageSize(normalizedRequest.getPageSize());
    OrgRunningSearchRepository.SearchResult result =
        orgRunningSearchRepository.search(normalizedRequest, cursorId, pageSize);

    OrgRunningResponse response = new OrgRunningResponse();
    response.setTotCont(result.totalCount());
    response.setNextKey(result.nextKey());
    response.setOrgnPrgslist(result.items().stream()
        .map(this::toOrgRunningItem)
        .collect(Collectors.toList()));
    return response;
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

    for (RunningWorkByCorrKeyRequestItem requestItem : request.getBswrList()) {
      String loanPcesMgmtNo = requestItem == null ? null : trimToNull(requestItem.getLoanPcesMgmtNo());
      if (loanPcesMgmtNo == null) {
        resultItems.add(resultItem(null, null, null, "LBM020001"));
        continue;
      }

      // 인스턴스 상태와 무관하게 corrKey 로 조회
      List<ProcessInstanceEntity> instances =
          processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc(loanPcesMgmtNo);
      if (instances == null || instances.isEmpty()) {
        resultItems.add(resultItem(loanPcesMgmtNo, null, null,"LBM020002"));
        continue;
      }

      for (ProcessInstanceEntity processInstance : instances) {
        Long rootInstId = processInstance.getRootInstId() == null
            ? processInstance.getInstId()
            : processInstance.getRootInstId();
        List<WorklistEntity> workItems = worklistRepository.findCurrentWorkItemByInstId(rootInstId);
        if (workItems == null || workItems.isEmpty()) {
          resultItems.add(resultItem(loanPcesMgmtNo, processInstance, null,"LBM020003"));
          continue;
        }

        for(WorklistEntity workItem : workItems){
          resultItems.add(resultItem(loanPcesMgmtNo, processInstance, workItem, null));
        }
      }
    }

    return response;
  }

  private static RunningWorkByCorrKeyResponseItem resultItem(
      String loanPcesMgmtNo,
      ProcessInstanceEntity processInstance,
      WorklistEntity workItem,
      String resultMessage) {
    RunningWorkByCorrKeyResponseItem item = new RunningWorkByCorrKeyResponseItem();
    item.setLoanPcesMgmtNo(loanPcesMgmtNo);
    item.setPrcsRsltCntn(resultMessage == null ? "LBM000000" : resultMessage);

    if (processInstance != null) {
      item.setPrgsSttsNm(processInstance.getStatus());
    }
    if (workItem != null) {
      item.setFncgBpmTaskTrcgNm(workItem.getTrcTag());
      item.setFncgBpmUworSttsCntn(workItem.getStatus());
      item.setHndrEmnb(workItem.getEndpoint());
    }
    return item;
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
  }

  private MyTodoItem toMyTodoItem(WorklistEntity worklist) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    MyTodoItem item = new MyTodoItem();

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
    item.setReptHndrFncgOrgnCode(instance == null ? null : instance.getInitGroupCd());
    item.setPrcdHndrEmnb(instance == null ? null : instance.getPrevCurrEp());
    item.setPrcdHndrFncgOrgnCode(instance == null ? null : instance.getPrevCurrGroupCd());
    item.setFncgBpmUworSttsCntn(worklist.getStatus());
    item.setStarDttm(instance == null ? null : instance.getStartedDate());
    item.setBefoHndrEmnb(worklist.getPrevEndpoint());
    item.setBefoFncgOrgnCode(worklist.getPrevGroupCd());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrNm(worklist.getResName());
    item.setHndrOrgnCode(firstNonBlank(worklist.getGroupCd(), worklist.getScope()));
    item.setScrnUrlAddr(worklist.getTool());
    item.setFncgBpmTaskLstId(worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(worklist.getInstId() == null ? null : String.valueOf(worklist.getInstId()));
    item.setDstOptnVal(String.valueOf(worklist.getDispatchOption()));
    item.setRuleAcmpVal(String.valueOf(worklist.getAssignType()));
    item.setMnorExstYn(worklist.getDelegated() == null ? "N" : worklist.getDelegated() ? "Y" : "N");
    item.setApvlYn(worklist.getApvlYn() == null ? "N" : worklist.getApvlYn() ? "Y" : "N");
    item.setImgeScanYn(worklist.getImgeScanYn() == null ? "N" : worklist.getImgeScanYn() ? "Y" : "N");
    return item;
  }

  private OrgRunningItem toOrgRunningItem(WorklistEntity worklist) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    OrgRunningItem item = new OrgRunningItem();

    item.setStarDttm(toLocalDateTime(instance == null ? null : instance.getStartedDate()));
    item.setFncgBswrDvsnCode(instance == null ? null : instance.getFncgBswrDvsnCode());
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setCustId(instance == null ? null : instance.getCustId());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setReptHndrEmnb(instance == null ? null : instance.getInitEp());
    item.setReptHndrFncgOrgnCode(instance == null ? null : instance.getInitGroupCd());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrOrgnCode(trimToNull(worklist.getScope()));
    item.setUworNm(worklist.getTitle());
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setUworStarDttm(toLocalDateTime(worklist.getStartDate()));
    item.setFncgBpmtaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        worklist.getInstId() == null ? null : String.valueOf(worklist.getInstId()));
    return item;
  }

  private static java.time.LocalDateTime toLocalDateTime(Date value) {
    return value == null
        ? null
        : java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(value.getTime()),
            java.time.ZoneId.systemDefault());
  }

  private static Long parseNextKey(String nextKey) {
    String value = trimToNull(nextKey);
    if (value == null) {
      return null;
    }
    try {
      long taskId = Long.parseLong(value);
      if (taskId <= 0) {
        throw new NumberFormatException("nextKey must be positive");
      }
      return taskId;
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "nextKey must be a positive fncgBpmTaskLstId",
          exception);
    }
  }

  private static int normalizePageSize(Integer pageSize) {
    if (pageSize == null) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
  }

  private static MyTodoRequest requireMyTodoRequest(MyTodoRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
    }
    return request;
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
