package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.search.dto.MyTodoItem;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;

class WorkSearchServiceImplTest {

  private static final long WORK_START = 1_750_000_000_000L;
  private static final long HOPE_DATE = 1_760_000_000_000L;

  private WorklistRepository worklistRepository;
  private WorkSearchServiceImpl service;

  @BeforeEach
  void setUp() {
    worklistRepository = mock(WorklistRepository.class);
    service = new WorkSearchServiceImpl(worklistRepository);
  }

  @Test
  void filtersByEverySupportedRequestFieldWithAndSemantics() {
    WorklistEntity worklist = completeWorklist(101L, WORK_START);
    when(worklistRepository.findToDo()).thenReturn(List.of(worklist));

    MyTodoResponse response = service.searchMyTodo(fullRequest());

    assertEquals(1, response.getTotCont());
    assertEquals(List.of("101"), taskIds(response));

    List<Consumer<MyTodoRequest>> mismatches = List.of(
        request -> request.setBswrClsfCode("OTHER"),
        request -> request.setCustId("OTHER"),
        request -> request.setFncgBswrDvsnCode("OTHER"),
        request -> request.setLoanCntcNo("OTHER"),
        request -> request.setLoanPcesMgmtNo("OTHER"),
        request -> request.setFncgSuptTrgtDvsnCode("OTHER"),
        request -> request.setLoanSubjDvsnCode("OTHER"),
        request -> request.setFncgMneyUsagClsfCode("OTHER"),
        request -> request.setFncgBpmTaskTrcgNm("OTHER"),
        request -> request.setFncgWndwOrgnCode("OTHER"),
        request -> request.setHndrEmnb("OTHER"));

    for (Consumer<MyTodoRequest> mismatch : mismatches) {
      MyTodoRequest request = fullRequest();
      mismatch.accept(request);
      assertTrue(service.searchMyTodo(request).getTodolist().isEmpty());
    }
  }

  @Test
  void appliesInclusiveWorkAndHopeDateRanges() {
    when(worklistRepository.findToDo()).thenReturn(List.of(completeWorklist(101L, WORK_START)));

    MyTodoRequest inclusive = fullRequest();
    inclusive.setStarDate(date(WORK_START));
    inclusive.setEndDate(date(WORK_START));
    inclusive.setHopeStarDate(date(HOPE_DATE));
    inclusive.setHopeEndDate(date(HOPE_DATE));
    assertEquals(1, service.searchMyTodo(inclusive).getTotCont());

    MyTodoRequest workStartsTooLate = fullRequest();
    workStartsTooLate.setStarDate(date(WORK_START + 1));
    assertEquals(0, service.searchMyTodo(workStartsTooLate).getTotCont());

    MyTodoRequest workEndsTooEarly = fullRequest();
    workEndsTooEarly.setEndDate(date(WORK_START - 1));
    assertEquals(0, service.searchMyTodo(workEndsTooEarly).getTotCont());

    MyTodoRequest hopeStartsTooLate = fullRequest();
    hopeStartsTooLate.setHopeStarDate(date(HOPE_DATE + 1));
    assertEquals(0, service.searchMyTodo(hopeStartsTooLate).getTotCont());

    MyTodoRequest hopeEndsTooEarly = fullRequest();
    hopeEndsTooEarly.setHopeEndDate(date(HOPE_DATE - 1));
    assertEquals(0, service.searchMyTodo(hopeEndsTooEarly).getTotCont());
  }

  @Test
  void sortsAndPaginatesWithOneBasedPageNumbers() {
    List<WorklistEntity> worklists = new ArrayList<>();
    for (long taskId = 1; taskId <= 25; taskId++) {
      worklists.add(minimalWorklist(taskId, WORK_START + taskId));
    }
    when(worklistRepository.findToDo()).thenReturn(worklists);

    MyTodoRequest descendingSecondPage = new MyTodoRequest();
    descendingSecondPage.setSortOrdrVal("DESC");
    descendingSecondPage.setPageNo(2);
    MyTodoResponse descending = service.searchMyTodo(descendingSecondPage);

    assertEquals(25, descending.getTotCont());
    assertEquals(List.of("5", "4", "3", "2", "1"), taskIds(descending));

    MyTodoRequest ascendingFirstPage = new MyTodoRequest();
    ascendingFirstPage.setSortOrdrVal("ASC");
    ascendingFirstPage.setPageNo(1);
    MyTodoResponse ascending = service.searchMyTodo(ascendingFirstPage);

    assertEquals(20, ascending.getTodolist().size());
    assertEquals("1", ascending.getTodolist().get(0).getFncgBpmTaskLstId());
    assertEquals("20", ascending.getTodolist().get(19).getFncgBpmTaskLstId());
  }

  @Test
  void mapsEveryResponseFieldAndExcludesClosedWorkItems() {
    WorklistEntity open = completeWorklist(101L, WORK_START);
    WorklistEntity completed = completeWorklist(102L, WORK_START + 2);
    completed.setStatus("COMPLETED");
    WorklistEntity cancelled = completeWorklist(103L, WORK_START + 3);
    cancelled.setStatus("CANCELLED");
    when(worklistRepository.findToDo()).thenReturn(List.of(open, completed, cancelled));

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
    request.setFncgWndwOrgnCode("GROUP");
    request.setHndrEmnb("handler");
    request.setPageNo(1);
    request.setSortOrdrVal("DESC");
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
    instance.setLaonHopeDate(date(HOPE_DATE));
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

  private static WorklistEntity minimalWorklist(long taskId, long startDate) {
    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(taskId + 100);
    worklist.setStartDate(date(startDate));
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
}
