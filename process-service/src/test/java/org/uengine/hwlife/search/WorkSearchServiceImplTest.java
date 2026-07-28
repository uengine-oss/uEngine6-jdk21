package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequestItem;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponseItem;
import org.uengine.kernel.Activity;

class WorkSearchServiceImplTest {

  private static final long WORK_START = 1_750_000_000_000L;
  private static final long HOPE_DATE = 1_760_000_000_000L;

  private MyTodoSearchRepository searchRepository;
  private ProcessInstanceRepository processInstanceRepository;
  private WorklistRepository worklistRepository;
  private WorkSearchServiceImpl service;

  @BeforeEach
  void setUp() {
    searchRepository = mock(MyTodoSearchRepository.class);
    processInstanceRepository = mock(ProcessInstanceRepository.class);
    worklistRepository = mock(WorklistRepository.class);
    service = new WorkSearchServiceImpl(
        searchRepository,
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
        .thenReturn(new SearchResult(List.of(minimalWorklist(21L)), 25));

    MyTodoResponse response = service.searchMyTodo(request);

    ArgumentCaptor<MyTodoRequest> requestCaptor = ArgumentCaptor.forClass(MyTodoRequest.class);
    verify(searchRepository).search(
        requestCaptor.capture(),
        eq(21L),
        eq(35),
        any(UserContext.class));
    assertSame(request, requestCaptor.getValue());
    assertEquals(25, response.getTotCont());
    assertEquals(List.of("21"), taskIds(response));
  }

  @Test
  void normalizesMissingAndOutOfRangeSizes() {
    when(searchRepository.search(
        any(MyTodoRequest.class),
        isNull(),
        anyInt(),
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(), 0));

    service.searchMyTodo(null);
    MyTodoRequest zeroSize = new MyTodoRequest();
    zeroSize.setPageSize(0);
    service.searchMyTodo(zeroSize);
    MyTodoRequest oversized = new MyTodoRequest();
    oversized.setPageSize(101);
    service.searchMyTodo(oversized);

    verify(searchRepository).search(
        any(MyTodoRequest.class),
        isNull(),
        eq(20),
        any(UserContext.class));
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
  void rejectsNextKeyThatIsNotAPositiveTaskId() {
    MyTodoRequest request = new MyTodoRequest();
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

    JsonNode json = new ObjectMapper().valueToTree(request);

    assertEquals("21", json.get("nextKey").asText());
    assertEquals(35, json.get("pageSize").asInt());
    assertTrue(json.has("nextKey"));
    assertTrue(json.has("pageSize"));
    assertFalse(json.has("cursor"));
    assertFalse(json.has("size"));
  }

  @Test
  void rejectsUnknownSortDirection() {
    MyTodoRequest request = new MyTodoRequest();
    request.setSortDirection("NEWEST");

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.searchMyTodo(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
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

    MyTodoResponse response = service.searchMyTodo(new MyTodoRequest());

    assertEquals(1, response.getTotCont());
    MyTodoItem item = response.getTodolist().get(0);
    assertEquals("BSWR", item.getBswrClsfCode());
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
    assertEquals("GROUP", item.getReptHndrFncgOrgnCode());
    assertEquals("previous", item.getPrcdHndrEmnb());
    assertEquals("PREV-GROUP", item.getPrcdHndrFncgOrgnCode());
    assertEquals("NEW", item.getFncgBpmUworSttsCntn());
    assertEquals(date(WORK_START - 100), item.getStarDttm());
    assertEquals("previous", item.getBefrHndrEmnb());
    assertEquals("PREV-GROUP", item.getBefrFncgOrgnCode());
    assertEquals("handler", item.getHndrEmnb());
    assertEquals("Handler Name", item.getHndrNm());
    assertEquals("GROUP", item.getHndrOrgnCode());
    assertEquals("form", item.getScrnUrlAddr());
    assertEquals("101", item.getFncgBpmTaskLstId());
    assertEquals("201", item.getFncgBpmPcesIntcId());
  }

  @Test
  void returnsEmptyRunningWorkResponseWithoutQueriesForEmptyRequest() {
    RunningWorkByCorrKeyResponse response =
        service.searchRunningWorkByCorrKey(new RunningWorkByCorrKeyRequest());

    assertEquals(List.of(), response.getBswrList());
    verify(processInstanceRepository, never()).findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING));
    verify(worklistRepository, never()).findCurrentWorkItemsByRootInstIds(anyCollection());
  }

  @Test
  void keepsRunningWorkRepositoryCallsConstantForOneHundredCorrKeys() {
    List<RunningWorkByCorrKeyRequestItem> requestItems = new ArrayList<>();
    List<ProcessInstanceEntity> instances = new ArrayList<>();
    List<WorklistEntity> workItems = new ArrayList<>();
    for (int index = 1; index <= 100; index++) {
      String corrKey = "CORR-" + index;
      requestItems.add(runningWorkRequestItem(corrKey));
      instances.add(runningInstance((long) index, corrKey));
      workItems.add(currentWorkItem((long) index, "TASK-" + index));
    }
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(instances);
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection())).thenReturn(workItems);

    RunningWorkByCorrKeyResponse response =
        service.searchRunningWorkByCorrKey(runningWorkRequest(requestItems));

    assertEquals(100, response.getBswrList().size());
    ArgumentCaptor<Collection<String>> corrKeys = collectionCaptor();
    verify(processInstanceRepository).findByCorrKeyInAndStatus(
        corrKeys.capture(), eq(Activity.STATUS_RUNNING));
    assertEquals(100, corrKeys.getValue().size());
    ArgumentCaptor<Collection<Long>> rootInstIds = collectionCaptor();
    verify(worklistRepository).findCurrentWorkItemsByRootInstIds(rootInstIds.capture());
    assertEquals(100, rootInstIds.getValue().size());
  }

  @Test
  void preservesRunningWorkInputOrderDuplicatesInstancesAndParallelWorkItems() {
    ProcessInstanceEntity first = runningInstance(11L, "CORR-A");
    ProcessInstanceEntity second = runningInstance(12L, "CORR-A");
    WorklistEntity firstTask = currentWorkItem(11L, "A-1");
    WorklistEntity parallelTask = currentWorkItem(11L, "A-2");
    WorklistEntity secondTask = currentWorkItem(12L, "A-3");
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(List.of(first, second));
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection()))
        .thenReturn(List.of(firstTask, parallelTask, secondTask));

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
        "No running BPM instance found for loanPcesMgmtNo=UNKNOWN",
        response.getBswrList().get(3).getPrcsrsltCntn());
  }

  @Test
  void reportsInvalidRunningWorkKeyAndInstanceWithoutActiveWork() {
    ProcessInstanceEntity instance = runningInstance(21L, "CORR-NO-WORK");
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(List.of(instance));
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection())).thenReturn(List.of());

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(runningWorkRequest(List.of(
        runningWorkRequestItem("   "),
        runningWorkRequestItem("CORR-NO-WORK"))));

    assertEquals(2, response.getBswrList().size());
    assertNull(response.getBswrList().get(0).getLoanPcesMgmtNo());
    assertEquals("loanPcesMgmtNo is required", response.getBswrList().get(0).getPrcsrsltCntn());
    assertEquals(
        "No active work item found for running BPM instance instId=21",
        response.getBswrList().get(1).getPrcsrsltCntn());
  }

  private static MyTodoRequest fullRequest() {
    MyTodoRequest request = new MyTodoRequest();
    request.setBswrClsfCode("BSWR");
    request.setCustId("CUST");
    request.setFncgBswrDvsnCode("LOAN");
    request.setLoanCntcNo("CONTACT");
    request.setLoanPcesMgmtNo("CORR-101");
    request.setFncgSuptTrgtDvsnCode("TARGET");
    request.setLoanSubjDvsnCode("SUBJECT");
    request.setFncgMneyUsagClsfCode("USAGE");
    request.setFncgBpmTaskTrcgNm("TRACE");
    request.setStarDate(date(WORK_START));
    request.setEndDate(date(WORK_START));
    request.setHopeStarDate(date(HOPE_DATE));
    request.setHopeEndDate(date(HOPE_DATE));
    request.setFncgWndwOrgnCode("GROUP");
    request.setHndrEmnb("handler");
    request.setNextKey("21");
    request.setPageSize(35);
    request.setSortOrdrVal("loanHopeDate");
    request.setSortDirection("ASC");
    return request;
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
    return response.getTodolist().stream()
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> ArgumentCaptor<Collection<T>> collectionCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
  }
}
