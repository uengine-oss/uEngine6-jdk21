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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
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
  }

  @Test
  void delegatesCompleteRequestNextKeyAndPageSizeToRepository() {
    MyTodoRequest request = fullRequest();
    when(searchRepository.search(
        any(MyTodoRequest.class),
        eq(21L),
        eq(35),
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(minimalWorklist(21L)), 25, "122"));

    MyTodoResponse response = service.searchMyTodo(request);

    ArgumentCaptor<MyTodoRequest> requestCaptor = ArgumentCaptor.forClass(MyTodoRequest.class);
    verify(searchRepository).search(
        requestCaptor.capture(),
        eq(21L),
        eq(35),
        any(UserContext.class));
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
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(), 0));

    assertBadRequest(() -> service.searchMyTodo(null));
    assertBadRequest(() -> service.searchMyTodo(new MyTodoRequest()));

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
        any(UserContext.class));
    verify(searchRepository).search(
        any(MyTodoRequest.class),
        isNull(),
        eq(100),
        any(UserContext.class));
  }

  @Test
  void returnsEmptyMyTodoResponseForBlankHandlerAndOrganization() {
    when(searchRepository.search(
        any(MyTodoRequest.class),
        isNull(),
        eq(10),
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(), 0));

    MyTodoRequest request = new MyTodoRequest();
    request.setHndrEmnb("");
    request.setFncgWndwOrgnCode("");
    request.setPageSize(10);

    MyTodoResponse response = service.searchMyTodo(request);

    assertEquals(0, response.getTotCont());
    assertNull(response.getNextKey());
    assertTrue(response.getTodoList().isEmpty());
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
    request.setStartDate(date(WORK_START));
    request.setHopeStartDate(date(HOPE_DATE));

    JsonNode json = new ObjectMapper().valueToTree(request);

    assertEquals("21", json.get("nextKey").asText());
    assertEquals(35, json.get("pageSize").asInt());
    assertEquals("START_DATE", json.get("sortOrdrVal").asText());
    assertTrue(json.has("nextKey"));
    assertTrue(json.has("pageSize"));
    assertTrue(json.has("sortOrdrVal"));
    assertTrue(json.has("startDate"));
    assertTrue(json.has("hopeStartDate"));
    assertFalse(json.has("starDate"));
    assertFalse(json.has("hopeStarDate"));
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
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(worklist), 1));

    MyTodoResponse response = service.searchMyTodo(requiredMyTodoRequest());

    assertEquals(1, response.getTotCont());
    MyTodoItem item = response.getTodoList().get(0);
    assertEquals("CUST", item.getCustId());
    assertEquals("LOAN", item.getFncgBswrDvsnCode());
    assertEquals("CONTACT", item.getLoanCntcNo());
    assertEquals("TARGET", item.getFncgSuptTrgtDvsnCode());
    assertEquals("SUBJECT", item.getLoanSubjDvsnCode());
    assertEquals("USAGE", item.getFncgMneyUsagClsfCode());
    assertEquals(date(HOPE_DATE), item.getLoanHopeDate());
    assertEquals("CORR-101", item.getLoanPcesMgmtNo());
    assertEquals("TRACE", item.getFncgBpmTaskTrcgNm());
    assertEquals(date(WORK_START), item.getUworStarDttm());
    assertEquals("Unit work", item.getUworNm());
    assertEquals("Loan process", item.getLoanPcesNm());
    assertEquals("reporter", item.getReptHndrEmnb());
    assertEquals("SCOPE", item.getReptHndrFncgOrgnCode());
    assertEquals("previous", item.getPrcdHndrEmnb());
    assertEquals("PREV-GROUP", item.getPrcdHndrFncgOrgnCode());
    assertEquals("NEW", item.getFncgBpmUworSttsCntn());
    assertEquals(date(WORK_START - 100), item.getStarDttm());
    assertEquals("previous", item.getBefrHndrEmnb());
    assertEquals("PREV-GROUP", item.getBefrFncgOrgnCode());
    assertEquals("handler", item.getHndrEmnb());
    assertEquals("Handler Name", item.getHndrNm());
    assertEquals("SCOPE", item.getHndrOrgnCode());
    assertEquals("form", item.getScrnUrlAddr());
    assertEquals("101", item.getFncgBpmTaskLstId());
    assertEquals("201", item.getFncgBpmPcesIntcId());
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
    assertEquals("102", response.getOrgnPrgslist().get(0).getFncgBpmtaskLstId());
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
    OrgRunningItem item = response.getOrgnPrgslist().get(0);
    assertEquals("LOAN", item.getFncgBswrDvsnCode());
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
    assertEquals("SCOPE", item.getHndrOrgnCode());
    assertEquals("Unit work", item.getUworNm());
    assertEquals("TRACE", item.getFncgBpmTaskTrcgNm());
    assertEquals("101", item.getFncgBpmtaskLstId());
    assertEquals("201", item.getFncgBpmPcesIntcId());
    assertEquals(
        java.time.LocalDateTime.ofInstant(
            date(WORK_START - 100).toInstant(),
            java.time.ZoneId.systemDefault()),
        item.getStarDttm());
    assertEquals(
        java.time.LocalDateTime.ofInstant(
            date(WORK_START).toInstant(),
            java.time.ZoneId.systemDefault()),
        item.getUworStarDttm());
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
        "No BPM instance found for loanPcesMgmtNo=UNKNOWN",
        response.getBswrList().get(3).getPrcsrsltCntn());
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
    assertEquals("loanPcesMgmtNo is required", response.getBswrList().get(0).getPrcsrsltCntn());
    assertEquals(
        "No work item found for BPM instance instId=21",
        response.getBswrList().get(1).getPrcsrsltCntn());
  }

  private static MyTodoRequest fullRequest() {
    MyTodoRequest request = new MyTodoRequest();
    request.setBpmBswrClsfCode("BSWR");
    request.setCustId("CUST");
    request.setFncgBswrDvsnCode("LOAN");
    request.setLoanCntcNo("CONTACT");
    request.setLoanPcesMgmtNo("CORR-101");
    request.setFncgSuptTrgtDvsnCode("TARGET");
    request.setLoanSubjDvsnCode("SUBJECT");
    request.setFncgMneyUsagClsfCode("USAGE");
    request.setFncgBpmTaskTrcgNm("TRACE");
    request.setStartDate(date(WORK_START));
    request.setEndDate(date(WORK_START));
    request.setHopeStartDate(date(HOPE_DATE));
    request.setHopeEndDate(date(HOPE_DATE));
    request.setFncgWndwOrgnCode("GROUP");
    request.setHndrEmnb("handler");
    request.setNextKey("21");
    request.setPageSize(35);
    return request;
  }

  private static MyTodoRequest requiredMyTodoRequest() {
    MyTodoRequest request = new MyTodoRequest();
    request.setHndrEmnb("handler");
    request.setFncgWndwOrgnCode("GROUP");
    request.setPageSize(20);
    return request;
  }

  private static void assertBadRequest(Runnable runnable) {
    ResponseStatusException exception = assertThrows(ResponseStatusException.class, runnable::run);
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  private static WorklistEntity completeWorklist(long taskId, long startDate) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
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

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(201L);
    worklist.setProcessInstance(instance);
    worklist.setTrcTag("TRACE");
    worklist.setStartDate(date(startDate));
    worklist.setTitle("Unit work");
    worklist.setDefName("Loan process");
    worklist.setAssignGroup("GROUP");
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
