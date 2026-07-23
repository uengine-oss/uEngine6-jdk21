package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.search.dto.*;

/**
 * BPM 통합 검색 REST API 구현. Repository 연동은 추후 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class WorkSearchServiceImpl implements WorkSearchService {

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final WorklistRepository worklistRepository;

  public WorkSearchServiceImpl(WorklistRepository worklistRepository) {
    this.worklistRepository = worklistRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public MyTodoResponse searchMyTodo(@RequestBody MyTodoRequest request) {
    MyTodoRequest normalizedRequest = request == null ? new MyTodoRequest() : request;
    int pageNo = normalizePageNo(normalizedRequest.getPageNo());

    List<MyTodoItem> filteredItems = worklistRepository.findToDo().stream()
        .filter(this::isOpenWorkItem)
        .filter(worklist -> matches(normalizedRequest, worklist))
        .sorted(myTodoSort(normalizedRequest.getSortOrdrVal()))
        .map(this::toMyTodoItem)
        .collect(Collectors.toList());

    int fromIndex = Math.min(pageNo * DEFAULT_PAGE_SIZE, filteredItems.size());
    int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, filteredItems.size());

    MyTodoResponse response = new MyTodoResponse();
    response.setTotCont(filteredItems.size());
    response.setTodolist(new ArrayList<>(filteredItems.subList(fromIndex, toIndex)));
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
  public WorklistByInstIdResponseItem searchWorklistByInstId(@RequestBody WorklistByInstIdRequest request) {
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

  private boolean isOpenWorkItem(WorklistEntity worklist) {
    String status = trimToNull(worklist.getStatus());
    return status == null
        || (!"COMPLETED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status));
  }

  private boolean matches(MyTodoRequest request, WorklistEntity worklist) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    return matchesText(request.getBswrClsfCode(), instance == null ? null : instance.getBsnsClsfCode())
        && matchesText(request.getCustId(), instance == null ? null : instance.getCustId())
        && matchesText(request.getFncgBswrDvsnCode(), instance == null ? null : instance.getFncgBswrDvsnCode())
        && matchesText(request.getLoanCntcNo(), instance == null ? null : instance.getLoanCntcNo())
        && matchesText(request.getFncgSuptTrgtDvsnCode(), instance == null ? null : instance.getFncgSuptTrgtDvsnCode())
        && matchesText(request.getLoanSubjDvsnCode(), instance == null ? null : instance.getLoanSubjDvsnCode())
        && matchesText(request.getFncgMneyUsagClsfCode(), instance == null ? null : instance.getFncgMneyUsagClsfCode())
        && matchesText(request.getFncgBpmTaskTrcgNm(), worklist.getTrcTag())
        && matchesText(request.getHndrEmnb(), worklist.getEndpoint())
        && matchesText(request.getFncgWndwOrgnCode(), firstNonBlank(worklist.getAssignGroup(), worklist.getScope()))
        && inRange(worklist.getStartDate(), request.getStarDate(), request.getEndDate())
        && inRange(instance == null ? null : instance.getLaonHopeDate(), request.getHopeStarDate(), request.getHopeEndDate());
  }

  private MyTodoItem toMyTodoItem(WorklistEntity worklist) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    MyTodoItem item = new MyTodoItem();

    item.setBswrClsfCode(instance == null ? null : instance.getBsnsClsfCode());
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

  private Comparator<WorklistEntity> myTodoSort(String sortOrdrVal) {
    Comparator<WorklistEntity> comparator = Comparator
        .comparing(WorklistEntity::getStartDate, Comparator.nullsLast(Date::compareTo))
        .thenComparing(WorklistEntity::getTaskId, Comparator.nullsLast(Long::compareTo));

    String normalized = trimToNull(sortOrdrVal);
    if (normalized == null || normalized.toUpperCase(Locale.ROOT).contains("DESC")) {
      return comparator.reversed();
    }
    return comparator;
  }

  private static int normalizePageNo(Integer pageNo) {
    if (pageNo == null || pageNo <= 0) {
      return 0;
    }
    return pageNo - 1;
  }

  private static boolean matchesText(String expected, String actual) {
    String expectedValue = trimToNull(expected);
    if (expectedValue == null) {
      return true;
    }
    String actualValue = trimToNull(actual);
    return actualValue != null && actualValue.equals(expectedValue);
  }

  private static boolean inRange(Date target, Date startInclusive, Date endInclusive) {
    if (target == null) {
      return startInclusive == null && endInclusive == null;
    }
    return (startInclusive == null || !target.before(startInclusive))
        && (endInclusive == null || !target.after(endInclusive));
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
