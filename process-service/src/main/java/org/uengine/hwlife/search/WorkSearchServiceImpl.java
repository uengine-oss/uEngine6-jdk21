package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
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
import org.uengine.five.service.RootInstanceResolver;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;
import org.uengine.hwlife.search.dto.BulkAssignSearchResponse;
import org.uengine.hwlife.search.dto.BulkAssignSearchResponseItem;
import org.uengine.hwlife.search.dto.MyProgressItem;
import org.uengine.hwlife.search.dto.MyProgressRequest;
import org.uengine.hwlife.search.dto.MyProgressResponse;
import org.uengine.hwlife.search.dto.MyTodoItem;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;
import org.uengine.hwlife.search.dto.OrgCompletedItem;
import org.uengine.hwlife.search.dto.OrgCompletedRequest;
import org.uengine.hwlife.search.dto.OrgCompletedResponse;
import org.uengine.hwlife.search.dto.OrgRunningItem;
import org.uengine.hwlife.search.dto.OrgRunningRequest;
import org.uengine.hwlife.search.dto.OrgRunningResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequestItem;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponseItem;
import org.uengine.hwlife.search.dto.WorklistByInstIdRequest;
import org.uengine.hwlife.search.dto.WorklistByInstIdResponse;
import org.uengine.hwlife.search.dto.WorklistByInstIdResponseItem;

/**
 * BPM 통합 검색 REST API 구현.
 */
@RestController
@CrossOrigin(origins = "*")
@Service
public class WorkSearchServiceImpl implements WorkSearchService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_DATE_RANGE_DAYS = 30;
  private static final String DEFAULT_RQST_DVSN_CODE = "N";

  private final MyTodoSearchRepository myTodoSearchRepository;
  private final OrgRunningSearchRepository orgRunningSearchRepository;
  private final BulkAssignSearchRepository bulkAssignSearchRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final WorklistRepository worklistRepository;
  private final RootInstanceResolver rootInstanceResolver;

  public WorkSearchServiceImpl(
      MyTodoSearchRepository myTodoSearchRepository,
      OrgRunningSearchRepository orgRunningSearchRepository,
      BulkAssignSearchRepository bulkAssignSearchRepository,
      ProcessInstanceRepository processInstanceRepository,
      WorklistRepository worklistRepository,
      RootInstanceResolver rootInstanceResolver) {
    this.myTodoSearchRepository = myTodoSearchRepository;
    this.orgRunningSearchRepository = orgRunningSearchRepository;
    this.bulkAssignSearchRepository = bulkAssignSearchRepository;
    this.processInstanceRepository = processInstanceRepository;
    this.worklistRepository = worklistRepository;
    this.rootInstanceResolver = rootInstanceResolver;
  }

  private static ResponseStatusException notImplemented(String operation) {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, operation + " is not implemented yet");
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

    Map<Long, ProcessInstanceEntity> rootInstances = rootInstanceResolver.preload(result.items());
    MyTodoResponse response = new MyTodoResponse();
    response.setTotCont(result.totalCount());
    response.setNextKey(result.nextKey());
    response.setTodoList(result.items().stream()
        .map(worklist -> toMyTodoItem(worklist, rootInstances))
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
    OrgRunningRequest normalizedRequest = normalizeOrgRunningRequest(request);

    Long cursorId = parseNextKey(normalizedRequest.getNextKey());
    OrgRunningSearchRepository.SearchResult result =
        orgRunningSearchRepository.search(
            normalizedRequest, cursorId, normalizedRequest.getPageSize());

    Map<Long, ProcessInstanceEntity> rootInstances = rootInstanceResolver.preload(result.items());
    OrgRunningResponse response = new OrgRunningResponse();
    response.setTotCont(result.totalCount());
    response.setNextKey(result.nextKey());
    response.setOrgnPrgsList(result.items().stream()
        .map(worklist -> toOrgRunningItem(worklist, rootInstances))
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
    EsbCommonHeader header = EsbRequestBodyAdvice.currentHeader();
    String belnOrgnCode = trimToNull(header != null ? header.getBelnOrgnCode() : null);
    if (belnOrgnCode == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "header.belnOrgnCode is required");
    }

    BulkAssignSearchRepository.SearchResult result = bulkAssignSearchRepository.search(request, belnOrgnCode);
    Map<Long, ProcessInstanceEntity> rootInstances = rootInstanceResolver.preload(result.items());
    BulkAssignSearchResponse response = new BulkAssignSearchResponse();
    response.setTotCont(result.totalCount());
    response.setBswrList(result.items().stream()
        .map(worklist -> toBulkAssignItem(worklist, rootInstances))
        .collect(Collectors.toList()));
    return response;
  }

  /**
   * 인스턴스 ID({@code fncgBpmPcesIntcId}) 기준 워크리스트(히스토리) 조회.
   * {@link ProcessInstanceRepository#findAllWorklistsByRootInstId} 와 동일하게
   * rootInstId 기준으로 서브프로세스 태스크까지 포함한다.
   */
  @Override
  @Transactional(readOnly = true)
  public WorklistByInstIdResponse searchWorklistByInstId(@RequestBody WorklistByInstIdRequest request) {
    WorklistByInstIdResponse response = new WorklistByInstIdResponse();
    List<WorklistByInstIdResponseItem> resultItems = new ArrayList<>();
    response.setBswrList(resultItems);

    String instIdText = request == null ? null : trimToNull(request.getFncgBpmPcesIntcId());
    if (instIdText == null) {
      return response;
    }

    Long instId;
    try {
      instId = Long.parseLong(instIdText);
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "fncgBpmPcesIntcId must be a number", e);
    }

    Long rootInstId = rootInstanceResolver.resolveRootInstId(instId);
    List<WorklistEntity> worklists =
        processInstanceRepository.findAllWorklistsByRootInstId(rootInstId);
    if (worklists == null || worklists.isEmpty()) {
      return response;
    }

    for (WorklistEntity worklist : worklists) {
      resultItems.add(toWorklistByInstIdItem(worklist));
    }
    return response;
  }

  /**
   * corrKey(대출프로세스관리번호) 기준 진행 중 업무 조회.
   * <p>
   * 결과코드(prcsRsltCntn):
   * <ul>
   *   <li>LBM000000 - 정상 (워크아이템 조회 성공)</li>
   *   <li>LBM020001 - 요청 항목의 대출프로세스관리번호(loanPcesMgmtNo)가 없음</li>
   *   <li>LBM020002 - corrKey로 프로세스 인스턴스를 찾지 못함</li>
   *   <li>LBM020003 - 인스턴스는 있으나 현재 워크아이템이 없음</li>
   * </ul>
   */
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

    for (RunningWorkByCorrKeyRequestItem requestItem : request.getBswrList()) {
      String loanPcesMgmtNo = requestItem == null ? null : trimToNull(requestItem.getLoanPcesMgmtNo());
      if (loanPcesMgmtNo == null) {
        resultItems.add(toRunningWorkByCorrKeyItem(null, null, null, "LBM020001"));
        continue;
      }

      List<ProcessInstanceEntity> instances =
          processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc(loanPcesMgmtNo);
      if (instances == null || instances.isEmpty()) {
        resultItems.add(toRunningWorkByCorrKeyItem(loanPcesMgmtNo, null, null, "LBM020002"));
        continue;
      }

      for (ProcessInstanceEntity processInstance : instances) {
        Long rootInstId = RootInstanceResolver.rootInstIdOf(processInstance);
        List<WorklistEntity> workItems = worklistRepository.findCurrentWorkItemByInstId(rootInstId);
        if (workItems == null || workItems.isEmpty()) {
          resultItems.add(toRunningWorkByCorrKeyItem(loanPcesMgmtNo, processInstance, null, "LBM020003"));
          continue;
        }

        for (WorklistEntity workItem : workItems) {
          resultItems.add(toRunningWorkByCorrKeyItem(loanPcesMgmtNo, processInstance, workItem, "LBM000000"));
        }
      }
    }

    return response;
  }

  private MyTodoItem toMyTodoItem(
      WorklistEntity worklist, Map<Long, ProcessInstanceEntity> rootInstances) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    Long instId = worklist.getInstId();
    ProcessInstanceEntity rootInstance = rootInstanceResolver.resolve(instId, rootInstances);
    MyTodoItem item = new MyTodoItem();

    item.setCustId(instance == null ? null : instance.getCustId());
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setUworStarDttm(worklist.getStartDate());
    item.setUworNm(worklist.getTitle());
    item.setReptHndrEmnb(rootInstance == null ? null : rootInstance.getInitEp());
    item.setReptHndrFncgOrgnCode(rootInstance == null ? null : rootInstance.getInitGroupCd());
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
    item.setFncgBpmTaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        instId == null ? null : String.valueOf(instId));
    item.setBswrDvsnVal(rootInstance == null ? null : rootInstance.getDefId());
    item.setFncgBpmPcesId(worklist.getDefId());
    item.setDstOptnVal(String.valueOf(worklist.getDispatchOption()));
    item.setRuleAcmpVal(String.valueOf(worklist.getAssignType()));
    item.setMnorExstYn(toYn(worklist.getDelegated()));
    item.setApvlYn(toYn(worklist.getApvlYn()));
    item.setImgeScanYn(toYn(worklist.getImgeScanYn()));
    return item;
  }

  private OrgRunningItem toOrgRunningItem(
      WorklistEntity worklist, Map<Long, ProcessInstanceEntity> rootInstances) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    Long instId = worklist.getInstId();
    ProcessInstanceEntity rootInstance = rootInstanceResolver.resolve(instId, rootInstances);
    OrgRunningItem item = new OrgRunningItem();

    item.setStarDttm(instance == null ? null : instance.getStartedDate());
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setCustId(instance == null ? null : instance.getCustId());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setReptHndrEmnb(rootInstance == null ? null : rootInstance.getInitEp());
    item.setReptHndrFncgOrgnCode(rootInstance == null ? null : rootInstance.getInitGroupCd());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrOrgnCode(trimToNull(worklist.getGroupCd()));
    item.setUworNm(worklist.getTitle());
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setUworStarDttm(worklist.getStartDate());
    item.setFncgBpmtaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        instance == null || instance.getInstId() == null
            ? null
            : String.valueOf(instance.getInstId()));
    item.setBswrDvsnVal(rootInstance == null ? null : rootInstance.getDefId());
    item.setFncgBpmPcesId(worklist.getDefId());
    return item;
  }

  /** MyProgress 매핑 — {@code bswrDvsnVal}=root {@code defId}, {@code fncgBpmPcesId}=worklist {@code defId}. */
  MyProgressItem toMyProgressItem(
      WorklistEntity worklist, Map<Long, ProcessInstanceEntity> rootInstances) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    Long instId = worklist.getInstId();
    ProcessInstanceEntity rootInstance = rootInstanceResolver.resolve(instId, rootInstances);
    MyProgressItem item = new MyProgressItem();
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setCustId(instance == null ? null : instance.getCustId());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setUworNm(worklist.getTitle());
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setReptHndrEmnb(rootInstance == null ? null : rootInstance.getInitEp());
    item.setReptHndrFncgOrgnCode(rootInstance == null ? null : rootInstance.getInitGroupCd());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrOrgnCode(trimToNull(worklist.getGroupCd()));
    item.setBpmBswrClsfCode(instance == null ? null : instance.getBswrClsfCode());
    item.setFncgBpmtaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        instId == null ? null : String.valueOf(instId));
    item.setBswrDvsnVal(rootInstance == null ? null : rootInstance.getDefId());
    item.setFncgBpmPcesId(worklist.getDefId());
    return item;
  }

  /** OrgCompleted 매핑 — {@code bswrDvsnVal}=root {@code defId}, {@code fncgBpmPcesId}=worklist {@code defId}. */
  OrgCompletedItem toOrgCompletedItem(
      WorklistEntity worklist, Map<Long, ProcessInstanceEntity> rootInstances) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    Long instId = worklist.getInstId();
    ProcessInstanceEntity rootInstance = rootInstanceResolver.resolve(instId, rootInstances);
    OrgCompletedItem item = new OrgCompletedItem();
    item.setStarDttm(instance == null ? null : instance.getStartedDate());
    item.setLoanCntcNo(instance == null ? null : instance.getLoanCntcNo());
    item.setFncgSuptTrgtDvsnCode(instance == null ? null : instance.getFncgSuptTrgtDvsnCode());
    item.setLoanSubjDvsnCode(instance == null ? null : instance.getLoanSubjDvsnCode());
    item.setCustId(instance == null ? null : instance.getCustId());
    item.setFncgMneyUsagClsfCode(instance == null ? null : instance.getFncgMneyUsagClsfCode());
    item.setLoanHopeDate(instance == null ? null : instance.getLoanHopeDate());
    item.setLoanPcesMgmtNo(instance == null ? null : instance.getCorrKey());
    item.setReptHndrEmnb(rootInstance == null ? null : rootInstance.getInitEp());
    item.setReptHndrFncgOrgnCode(rootInstance == null ? null : rootInstance.getInitGroupCd());
    item.setEndDttm(instance == null ? null : instance.getFinishedDate());
    item.setBpmBswrClsfCode(instance == null ? null : instance.getBswrClsfCode());
    item.setFncgBpmtaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        instId == null ? null : String.valueOf(instId));
    item.setBswrDvsnVal(rootInstance == null ? null : rootInstance.getDefId());
    item.setFncgBpmPcesId(worklist.getDefId());
    return item;
  }

  private BulkAssignSearchResponseItem toBulkAssignItem(
    WorklistEntity worklist,
    Map<Long, ProcessInstanceEntity> rootInstances) {
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    ProcessInstanceEntity rootInstance = rootInstanceResolver.resolve(worklist.getInstId(), rootInstances);
    BulkAssignSearchResponseItem item = new BulkAssignSearchResponseItem();
    item.setBpmBswrClsfCode(instance == null ? null : instance.getBswrClsfCode());
    item.setFncgBpmTaskLstId(worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(worklist.getInstId() == null ? null : String.valueOf(worklist.getInstId()));
    item.setUworNm(worklist.getTitle());
    item.setBswrDvsnVal(rootInstance == null ? null : rootInstance.getDefId());

    return item;
  }

  private static WorklistByInstIdResponseItem toWorklistByInstIdItem(WorklistEntity worklist) {
    WorklistByInstIdResponseItem item = new WorklistByInstIdResponseItem();
    item.setFncgBpmTaskTrcgNm(worklist.getTrcTag());
    item.setUworNm(worklist.getTitle());
    item.setHndrEmnb(worklist.getEndpoint());
    item.setHndrNm(worklist.getResName());
    item.setHndrOrgnCode(firstNonBlank(worklist.getGroupCd(), worklist.getScope()));
    item.setUworStarDttm(worklist.getStartDate());
    item.setUworEndDttm(worklist.getEndDate());
    item.setFncgBpmUworSttsCntn(worklist.getStatus());
    item.setFncgBpmTaskLstId(
        worklist.getTaskId() == null ? null : String.valueOf(worklist.getTaskId()));
    item.setFncgBpmPcesIntcId(
        worklist.getInstId() == null ? null : String.valueOf(worklist.getInstId()));

    return item;
  }


  private static RunningWorkByCorrKeyResponseItem toRunningWorkByCorrKeyItem(
    String loanPcesMgmtNo,
    ProcessInstanceEntity processInstance,
    WorklistEntity workItem,
    String resultMessage) {
    RunningWorkByCorrKeyResponseItem item = new RunningWorkByCorrKeyResponseItem();
    item.setLoanPcesMgmtNo(loanPcesMgmtNo);
    item.setPrcsRsltCntn(resultMessage);

    if (processInstance != null) {
      item.setPrgsSttsNm(processInstance.getStatus());
    }
    if (workItem != null) {
      item.setFncgBpmTaskTrcgNm(workItem.getTrcTag());
      item.setFncgBpmUworSttsCntn(workItem.getStatus());
      item.setHndrEmnb(workItem.getEndpoint());
      item.setApvlYn(toYn(workItem.getApvlYn()));
      item.setImgeScanYn(toYn(workItem.getImgeScanYn()));
    }

    return item;
  }

  private static String toYn(Boolean value) {
    return value == null ? "N" : value ? "Y" : "N";
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

  /**
   * 조직 진행 검색 요청 정규화.
   * <ul>
   *   <li>{@code rqstDvsnCode}: 없으면 {@code N} (진행기관)</li>
   *   <li>{@code pageSize}: 없으면 {@link #DEFAULT_PAGE_SIZE} (1~{@link #MAX_PAGE_SIZE})</li>
   *   <li>날짜: 둘 다 없으면 (오늘-30일)~오늘 /
   *       시작만 없으면 종료-30일 / 종료만 없으면 시작+30일</li>
   * </ul>
   */
  private static OrgRunningRequest normalizeOrgRunningRequest(OrgRunningRequest request) {
    OrgRunningRequest normalized = request == null ? new OrgRunningRequest() : request;

    if (trimToNull(normalized.getRqstDvsnCode()) == null) {
      normalized.setRqstDvsnCode(DEFAULT_RQST_DVSN_CODE);
    }

    normalized.setPageSize(normalizePageSize(normalized.getPageSize()));

    Date start = normalized.getRqstStarDate();
    Date end = normalized.getRqstEndDate();
    if (start == null && end == null) {
      end = new Date();
      start = plusDays(end, -DEFAULT_DATE_RANGE_DAYS);
    } else if (start == null) {
      start = plusDays(end, -DEFAULT_DATE_RANGE_DAYS);
    } else if (end == null) {
      end = plusDays(start, DEFAULT_DATE_RANGE_DAYS);
    }
    normalized.setRqstStarDate(start);
    normalized.setRqstEndDate(end);
    return normalized;
  }

  private static Date plusDays(Date value, int days) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
    calendar.setTime(value);
    calendar.add(Calendar.DAY_OF_MONTH, days);
    return calendar.getTime();
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
