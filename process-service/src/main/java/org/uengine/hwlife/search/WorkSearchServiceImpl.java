package org.uengine.hwlife.search;

import java.util.Arrays;
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
import org.uengine.hwlife.search.dto.*;

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

  public WorkSearchServiceImpl(MyTodoSearchRepository myTodoSearchRepository) {
    this.myTodoSearchRepository = myTodoSearchRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) {
    MyTodoRequest normalizedRequest = request == null ? new MyTodoRequest() : request;
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

    RunningWorkByCorrKeyResponseItem item = new RunningWorkByCorrKeyResponseItem();
    item.setLoanPcesMgmtNo("LOAN-2026-0001");
    item.setFncgBpmTaskTrcgNm("FN013_S03_402");
    item.setFncgBpmUworSttsCntn("NEW");
    item.setPrgsSttsNm("RUNNING");
    item.setPrcsrsltCntn("정상(인스턴스: RUNNING, 단위업무상태: NEW)");
  
    response.setBswrList(Arrays.asList(item));
    return response;
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
    item.setLoanHopeDate(instance == null ? null : instance.getLaonHopeDate());
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
