package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
import org.uengine.hwlife.search.MyTodoSearchRepository.SearchResult;
import org.uengine.hwlife.search.dto.MyTodoItem;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;
import org.uengine.hwlife.search.dto.OrgRunningItem;
import org.uengine.hwlife.search.dto.OrgRunningRequest;
import org.uengine.hwlife.search.dto.OrgRunningResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequestItem;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponseItem;
import org.uengine.kernel.Activity;

class WorkSearchServiceImplTest {

  private static final long WORK_START = 1_750_000_000_000L;
  private static final long HOPE_DATE = 1_760_000_000_000L;
  private static final String HEADER_EMNB = "handler";
  private static final String HEADER_BELN_ORGN_CODE = "GROUP";

  private MyTodoSearchRepository searchRepository;
  private OrgRunningSearchRepository orgRunningSearchRepository;
  private ProcessInstanceRepository processInstanceRepository;
  private WorklistRepository worklistRepository;
  private WorkSearchServiceImpl service;

  @BeforeEach
  void setUp() {
    searchRepository = mock(MyTodoSearchRepository.class);
    orgRunningSearchRepository = mock(OrgRunningSearchRepository.class);
    processInstanceRepository = mock(ProcessInstanceRepository.class);
    worklistRepository = mock(WorklistRepository.class);
    service = new WorkSearchServiceImpl(
        searchRepository,
        orgRunningSearchRepository,
        processInstanceRepository,
        worklistRepository);
    bindEsbHeader(HEADER_EMNB, HEADER_BELN_ORGN_CODE);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void delegatesCompleteRequestNextKeyAndPageSizeToRepository() {
    MyTodoRequest request = fullRequest();
    when(searchRepository.search(
        any(MyTodoRequest.class),
        eq(21L),
        eq(35),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE)))
        .thenReturn(new SearchResult(List.of(minimalWorklist(21L)), 25, "122"));

    MyTodoResponse response = service.searchMyTodo(request);

    ArgumentCaptor<MyTodoRequest> requestCaptor = ArgumentCaptor.forClass(MyTodoRequest.class);
    verify(searchRepository).search(
        requestCaptor.capture(),
        eq(21L),
        eq(35),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE));
    assertSame(request, requestCaptor.getValue());
    assertEquals(25, response.getTotCont());
    assertEquals("122", response.getNextKey());
    assertEquals(List.of("21"), taskIds(response));
  }

  @Test
  void requiresMyTodoPageSizeAndNormalizesOutOfRangeSizes() {
    when(searchRepository.search(
        any(MyTodoRequest.class),
        isNull(),
        anyInt(),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE)))
        .thenReturn(new SearchResult(List.of(), 0));

    assertBadRequest(() -> service.searchMyTodo(null));

    MyTodoRequest zeroSize = requiredMyTodoRequest();
    zeroSize.setPageSize(0);
    service.searchMyTodo(zeroSize);
    MyTodoRequest oversized = requiredMyTodoRequest();
    oversized.setPageSize(101);
    service.searchMyTodo(oversized);

    verify(searchRepository).search(
        any(MyTodoRequest.class),
        isNull(),
        eq(1),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE));
    verify(searchRepository).search(
        any(MyTodoRequest.class),
        isNull(),
        eq(100),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE));
  }

  @Test
  void requiresHeaderEmnb() {
    bindEsbHeader(null, HEADER_BELN_ORGN_CODE);

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.searchMyTodo(requiredMyTodoRequest()));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    verify(searchRepository, never()).search(
        any(MyTodoRequest.class),
        any(),
        anyInt(),
        any(),
        any());
  }

  @Test
  void rejectsNextKeyThatIsNotAPositiveTaskId() {
    MyTodoRequest request = requiredMyTodoRequest();
    request.setNextKey("not-a-task-id");

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.searchMyTodo(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void serializesScrollRequestWithSpecifiedFieldNames() {
    MyTodoRequest request = new MyTodoRequest();
    request.setNextKey("21");
    request.setPageSize(35);
    request.setSortOrdrVal("START_DATE");
    request.setStarDate(date(WORK_START));
    request.setHopeStarDate(date(HOPE_DATE));

    JsonNode json = new ObjectMapper().valueToTree(request);

    assertEquals("21", json.get("nextKey").asText());
    assertEquals(35, json.get("pageSize").asInt());
    assertEquals("START_DATE", json.get("sortOrdrVal").asText());
    assertTrue(json.has("nextKey"));
    assertTrue(json.has("pageSize"));
    assertTrue(json.has("sortOrdrVal"));
    assertTrue(json.has("starDate"));
    assertTrue(json.has("hopeStarDate"));
    assertFalse(json.has("startDate"));
    assertFalse(json.has("hopeStartDate"));
    assertFalse(json.has("cursor"));
    assertFalse(json.has("size"));
  }

  @Test
  void mapsEveryResponseFieldFromPagedRepositoryResult() {
    WorklistEntity worklist = completeWorklist(101L, WORK_START);
    when(searchRepository.search(
        any(MyTodoRequest.class),
        isNull(),
        eq(20),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE)))
        .thenReturn(new SearchResult(List.of(worklist), 1));

    MyTodoResponse response = service.searchMyTodo(requiredMyTodoRequest());

    assertEquals(1, response.getTotCont());
    MyTodoItem item = response.getTodoList().get(0);
    assertEquals("CUST", item.getCustId());
    assertEquals("CONTACT", item.getLoanCntcNo());
    assertEquals("TARGET", item.getFncgSuptTrgtDvsnCode());
    assertEquals("SUBJECT", item.getLoanSubjDvsnCode());
    assertEquals("USAGE", item.getFncgMneyUsagClsfCode());
    assertEquals(date(HOPE_DATE), item.getLoanHopeDate());
    assertEquals("CORR-101", item.getLoanPcesMgmtNo());
    assertEquals("TRACE", item.getFncgBpmTaskTrcgNm());
    assertEquals(date(WORK_START), item.getUworStarDttm());
    assertEquals("Unit work", item.getUworNm());
    assertEquals("reporter", item.getReptHndrEmnb());
    assertEquals("INIT-GROUP", item.getReptHndrFncgOrgnCode());
    assertEquals("previous", item.getPrcdHndrEmnb());
    assertEquals("PREV-GROUP", item.getPrcdHndrFncgOrgnCode());
    assertEquals("NEW", item.getFncgBpmUworSttsCntn());
    assertEquals(date(WORK_START - 100), item.getStarDttm());
    assertEquals("previous", item.getBefoHndrEmnb());
    assertEquals("PREV-GROUP", item.getBefoFncgOrgnCode());
    assertEquals("handler", item.getHndrEmnb());
    assertEquals("Handler Name", item.getHndrNm());
    assertEquals("GROUP", item.getHndrOrgnCode());
    assertEquals("form", item.getScrnUrlAddr());
    assertEquals("101", item.getFncgBpmTaskLstId());
    assertEquals("201", item.getFncgBpmPcesIntcId());
    assertEquals("line_1", item.getFncgBpmPcesId());
  }

  @Test
  void delegatesOrgRunningRequestNextKeyAndPageSizeToRepository() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setNextKey("101");
    request.setPageSize(35);
    request.setSortOrdrVal("loanHopeDate");
    when(orgRunningSearchRepository.search(request, 101L, 35))
        .thenReturn(new OrgRunningSearchRepository.SearchResult(
            List.of(minimalWorklist(102L)),
            40,
            "103"));

    OrgRunningResponse response = service.searchOrgRunning(request);

    verify(orgRunningSearchRepository).search(request, 101L, 35);
    assertEquals(40, response.getTotCont());
    assertEquals("103", response.getNextKey());
    assertEquals("102", response.getOrgnPrgsList().get(0).getFncgBpmtaskLstId());
  }

  @Test
  void normalizesOrgRunningPageSizeAndRejectsInvalidScrollValues() {
    when(orgRunningSearchRepository.search(any(OrgRunningRequest.class), isNull(), eq(20)))
        .thenReturn(new OrgRunningSearchRepository.SearchResult(List.of(), 0));
    service.searchOrgRunning(null);

    OrgRunningRequest invalidKey = new OrgRunningRequest();
    invalidKey.setNextKey("zero");
    ResponseStatusException keyException = assertThrows(
        ResponseStatusException.class,
        () -> service.searchOrgRunning(invalidKey));

    verify(orgRunningSearchRepository).search(any(OrgRunningRequest.class), isNull(), eq(20));
    assertEquals(HttpStatus.BAD_REQUEST, keyException.getStatusCode());
  }

  @Test
  void serializesOrgRunningScrollAndSortContractWithoutPageNumber() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setNextKey("101");
    request.setPageSize(35);
    request.setSortOrdrVal("startedDate");

    JsonNode json = new ObjectMapper().valueToTree(request);

    assertEquals("101", json.get("nextKey").asText());
    assertEquals(35, json.get("pageSize").asInt());
    assertEquals("startedDate", json.get("sortOrdrVal").asText());
    assertFalse(json.has("sort" + "Direction"));
    assertFalse(json.has("pageNo"));
  }

  @Test
  void mapsEveryOrgRunningResponseField() {
    WorklistEntity worklist = completeWorklist(101L, WORK_START);
    ProcessInstanceEntity instance = worklist.getProcessInstance();
    instance.setInitComCd("REQUEST-COMPANY");
    instance.setInitGroupCd("REQUEST-GROUP");
    worklist.setStartDate(new java.sql.Date(WORK_START));
    when(orgRunningSearchRepository.search(any(OrgRunningRequest.class), isNull(), eq(20)))
        .thenReturn(new OrgRunningSearchRepository.SearchResult(List.of(worklist), 1));

    OrgRunningResponse response = service.searchOrgRunning(new OrgRunningRequest());

    assertEquals(1, response.getTotCont());
    OrgRunningItem item = response.getOrgnPrgsList().get(0);
    assertEquals("CONTACT", item.getLoanCntcNo());
    assertEquals("TARGET", item.getFncgSuptTrgtDvsnCode());
    assertEquals("SUBJECT", item.getLoanSubjDvsnCode());
    assertEquals("CUST", item.getCustId());
    assertEquals("USAGE", item.getFncgMneyUsagClsfCode());
    assertEquals(date(HOPE_DATE), item.getLoanHopeDate());
    assertEquals("CORR-101", item.getLoanPcesMgmtNo());
    assertEquals("reporter", item.getReptHndrEmnb());
    assertEquals("REQUEST-GROUP", item.getReptHndrFncgOrgnCode());
    assertEquals("handler", item.getHndrEmnb());
    assertEquals("GROUP", item.getHndrOrgnCode());
    assertEquals("Unit work", item.getUworNm());
    assertEquals("TRACE", item.getFncgBpmTaskTrcgNm());
    assertEquals("101", item.getFncgBpmtaskLstId());
    assertEquals("201", item.getFncgBpmPcesIntcId());
    assertEquals(date(WORK_START - 100), item.getStarDttm());
    assertEquals(new java.sql.Date(WORK_START), item.getUworStarDttm());
    assertEquals("line_1", item.getFncgBpmPcesId());
  }

  @Test
  void mapsFncgBpmPcesIdFromRootInstanceDefIdForSubProcessWorklist() {
    WorklistEntity worklist = completeWorklist(101L, WORK_START);
    ProcessInstanceEntity subInstance = worklist.getProcessInstance();
    subInstance.setInstId(202L);
    subInstance.setRootInstId(100L);
    subInstance.setDefId("line_2");
    worklist.setInstId(202L);

    ProcessInstanceEntity rootInstance = new ProcessInstanceEntity();
    rootInstance.setInstId(100L);
    rootInstance.setRootInstId(100L);
    rootInstance.setDefId("line_1");
    when(processInstanceRepository.findAllById(any()))
        .thenReturn(List.of(rootInstance));
    when(searchRepository.search(
        any(MyTodoRequest.class),
        isNull(),
        eq(20),
        eq(HEADER_EMNB),
        eq(HEADER_BELN_ORGN_CODE)))
        .thenReturn(new SearchResult(List.of(worklist), 1));

    MyTodoResponse response = service.searchMyTodo(requiredMyTodoRequest());

    assertEquals("line_1", response.getTodoList().get(0).getFncgBpmPcesId());
    assertEquals("202", response.getTodoList().get(0).getFncgBpmPcesIntcId());
  }

  @Test
  void returnsEmptyRunningWorkResponseWithoutQueriesForEmptyRequest() {
    RunningWorkByCorrKeyResponse response =
        service.searchRunningWorkByCorrKey(new RunningWorkByCorrKeyRequest());

    assertEquals(List.of(), response.getBswrList());
    verify(processInstanceRepository, never()).findByCorrKeyOrderByStartedDateDescInstIdDesc(any());
    verify(worklistRepository, never()).findCurrentWorkItemByInstId(any());
  }

  @Test
  void looksUpEveryRequestedCorrKeyUsingLatestMainContract() {
    List<RunningWorkByCorrKeyRequestItem> requestItems = new ArrayList<>();
    for (int index = 1; index <= 100; index++) {
      String corrKey = "CORR-" + index;
      long instanceId = index;
      requestItems.add(runningWorkRequestItem(corrKey));
      when(processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc(corrKey))
          .thenReturn(List.of(runningInstance(instanceId, corrKey)));
      when(worklistRepository.findCurrentWorkItemByInstId(instanceId))
          .thenReturn(List.of(currentWorkItem(instanceId, "TASK-" + index)));
    }

    RunningWorkByCorrKeyResponse response =
        service.searchRunningWorkByCorrKey(runningWorkRequest(requestItems));

    assertEquals(100, response.getBswrList().size());
    verify(processInstanceRepository, times(100)).findByCorrKeyOrderByStartedDateDescInstIdDesc(any());
    verify(worklistRepository, times(100)).findCurrentWorkItemByInstId(any());
  }

  @Test
  void preservesRunningWorkInputOrderDuplicatesInstancesAndParallelWorkItems() {
    ProcessInstanceEntity first = runningInstance(11L, "CORR-A");
    ProcessInstanceEntity second = runningInstance(12L, "CORR-A");
    WorklistEntity firstTask = currentWorkItem(11L, "A-1");
    WorklistEntity parallelTask = currentWorkItem(11L, "A-2");
    WorklistEntity secondTask = currentWorkItem(12L, "A-3");
    when(processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc("CORR-A")).thenReturn(List.of(first, second));
    when(processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc("UNKNOWN")).thenReturn(List.of());
    when(worklistRepository.findCurrentWorkItemByInstId(11L))
        .thenReturn(List.of(firstTask, parallelTask));
    when(worklistRepository.findCurrentWorkItemByInstId(12L))
        .thenReturn(List.of(secondTask));

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(runningWorkRequest(List.of(
        runningWorkRequestItem("CORR-A"),
        runningWorkRequestItem("UNKNOWN"),
        runningWorkRequestItem("CORR-A"))));

    assertEquals(
        Arrays.asList("A-1", "A-2", "A-3", null, "A-1", "A-2", "A-3"),
        response.getBswrList().stream()
            .map(RunningWorkByCorrKeyResponseItem::getFncgBpmTaskTrcgNm)
            .toList());
    assertEquals(
        "LBM020002",
        response.getBswrList().get(3).getPrcsRsltCntn());
  }

  @Test
  void reportsInvalidRunningWorkKeyAndInstanceWithoutActiveWork() {
    ProcessInstanceEntity instance = runningInstance(21L, "CORR-NO-WORK");
    when(processInstanceRepository.findByCorrKeyOrderByStartedDateDescInstIdDesc("CORR-NO-WORK")).thenReturn(List.of(instance));
    when(worklistRepository.findCurrentWorkItemByInstId(21L)).thenReturn(List.of());

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(runningWorkRequest(List.of(
        runningWorkRequestItem("   "),
        runningWorkRequestItem("CORR-NO-WORK"))));

    assertEquals(2, response.getBswrList().size());
    assertNull(response.getBswrList().get(0).getLoanPcesMgmtNo());
    assertEquals("LBM020001", response.getBswrList().get(0).getPrcsRsltCntn());
    assertEquals("LBM020003", response.getBswrList().get(1).getPrcsRsltCntn());
  }

  private static void bindEsbHeader(String emnb, String belnOrgnCode) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    ServletRequestAttributes attrs = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(attrs);
    if (emnb == null && belnOrgnCode == null) {
      return;
    }
    EsbCommonHeader header = new EsbCommonHeader();
    header.setEmnb(emnb);
    header.setBelnOrgnCode(belnOrgnCode);
    attrs.setAttribute(EsbRequestBodyAdvice.HEADER_ATTR, header, RequestAttributes.SCOPE_REQUEST);
  }

  private static MyTodoRequest fullRequest() {
    MyTodoRequest request = new MyTodoRequest();
    request.setBpmBswrClsfCode("BSWR");
    request.setCustId("CUST");
    request.setLoanCntcNo("CONTACT");
    request.setFncgSuptTrgtDvsnCode("TARGET");
    request.setLoanSubjDvsnCode("SUBJECT");
    request.setFncgMneyUsagClsfCode("USAGE");
    request.setStarDate(date(WORK_START));
    request.setEndDate(date(WORK_START));
    request.setHopeStarDate(date(HOPE_DATE));
    request.setHopeEndDate(date(HOPE_DATE));
    request.setFncgWndwOrgnCode("GROUP");
    request.setNextKey("21");
    request.setPageSize(35);
    return request;
  }

  private static MyTodoRequest requiredMyTodoRequest() {
    MyTodoRequest request = new MyTodoRequest();
    request.setPageSize(20);
    return request;
  }

  private static void assertBadRequest(Runnable runnable) {
    ResponseStatusException exception = assertThrows(ResponseStatusException.class, runnable::run);
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  private static WorklistEntity completeWorklist(long taskId, long startDate) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(201L);
    instance.setRootInstId(201L);
    instance.setDefId("line_1");
    instance.setBswrClsfCode("BSWR");
    instance.setCustId("CUST");
    instance.setFncgBswrDvsnCode("LOAN");
    instance.setLoanCntcNo("CONTACT");
    instance.setCorrKey("CORR-101");
    instance.setFncgSuptTrgtDvsnCode("TARGET");
    instance.setLoanSubjDvsnCode("SUBJECT");
    instance.setFncgMneyUsagClsfCode("USAGE");
    instance.setLoanHopeDate(date(HOPE_DATE));
    instance.setStartedDate(date(WORK_START - 100));
    instance.setDefName("Instance process");
    instance.setInitEp("reporter");
    instance.setInitGroupCd("INIT-GROUP");
    instance.setPrevCurrEp("previous");
    instance.setPrevCurrGroupCd("PREV-GROUP");

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(201L);
    worklist.setProcessInstance(instance);
    worklist.setTrcTag("TRACE");
    worklist.setStartDate(date(startDate));
    worklist.setTitle("Unit work");
    worklist.setDefName("Loan process");
    worklist.setGroupCd("GROUP");
    worklist.setScope("SCOPE");
    worklist.setPrevEndpoint("previous");
    worklist.setPrevGroupCd("PREV-GROUP");
    worklist.setStatus("NEW");
    worklist.setEndpoint("handler");
    worklist.setResName("Handler Name");
    worklist.setTool("form");
    return worklist;
  }

  private static WorklistEntity minimalWorklist(long taskId) {
    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(taskId + 100);
    worklist.setStatus("NEW");
    return worklist;
  }

  private static List<String> taskIds(MyTodoResponse response) {
    return response.getTodoList().stream()
        .map(MyTodoItem::getFncgBpmTaskLstId)
        .toList();
  }

  private static Date date(long value) {
    return new Date(value);
  }

  private static RunningWorkByCorrKeyRequest runningWorkRequest(
      List<RunningWorkByCorrKeyRequestItem> requestItems) {
    RunningWorkByCorrKeyRequest request = new RunningWorkByCorrKeyRequest();
    request.setBswrList(requestItems);
    return request;
  }

  private static RunningWorkByCorrKeyRequestItem runningWorkRequestItem(String corrKey) {
    RunningWorkByCorrKeyRequestItem item = new RunningWorkByCorrKeyRequestItem();
    item.setLoanPcesMgmtNo(corrKey);
    return item;
  }

  private static ProcessInstanceEntity runningInstance(Long instId, String corrKey) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instId);
    instance.setRootInstId(instId);
    instance.setCorrKey(corrKey);
    instance.setStatus(Activity.STATUS_RUNNING);
    return instance;
  }

  private static WorklistEntity currentWorkItem(Long rootInstId, String tracingTag) {
    WorklistEntity workItem = new WorklistEntity();
    workItem.setRootInstId(rootInstId);
    workItem.setTrcTag(tracingTag);
    workItem.setStatus("NEW");
    return workItem;
  }

}
