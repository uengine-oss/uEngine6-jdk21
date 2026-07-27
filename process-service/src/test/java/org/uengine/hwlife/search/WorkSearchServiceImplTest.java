package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.MyTodoSearchRepository.SearchResult;
import org.uengine.hwlife.search.dto.MyTodoItem;
import org.uengine.hwlife.search.dto.MyTodoRequest;
import org.uengine.hwlife.search.dto.MyTodoResponse;

class WorkSearchServiceImplTest {

  private static final long WORK_START = 1_750_000_000_000L;
  private static final long HOPE_DATE = 1_760_000_000_000L;

  private MyTodoSearchRepository searchRepository;
  private WorkSearchServiceImpl service;

  @BeforeEach
  void setUp() {
    searchRepository = mock(MyTodoSearchRepository.class);
    service = new WorkSearchServiceImpl(searchRepository);
  }

  @Test
  void delegatesCompleteRequestAndOneBasedPageToRepository() {
    MyTodoRequest request = fullRequest();
    when(searchRepository.search(
        any(MyTodoRequest.class),
        eq(1),
        eq(20),
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(minimalWorklist(21L)), 25));

    MyTodoResponse response = service.searchMyTodo(request);

    ArgumentCaptor<MyTodoRequest> requestCaptor = ArgumentCaptor.forClass(MyTodoRequest.class);
    verify(searchRepository).search(
        requestCaptor.capture(),
        eq(1),
        eq(20),
        any(UserContext.class));
    assertSame(request, requestCaptor.getValue());
    assertEquals(25, response.getTotCont());
    assertEquals(List.of("21"), taskIds(response));
  }

  @Test
  void normalizesMissingAndNonPositivePageNumbersToFirstPage() {
    when(searchRepository.search(
        any(MyTodoRequest.class),
        eq(0),
        eq(20),
        any(UserContext.class)))
        .thenReturn(new SearchResult(List.of(), 0));

    service.searchMyTodo(null);
    MyTodoRequest zeroPage = new MyTodoRequest();
    zeroPage.setPageNo(0);
    service.searchMyTodo(zeroPage);

    verify(searchRepository, org.mockito.Mockito.times(2)).search(
        any(MyTodoRequest.class),
        eq(0),
        eq(20),
        any(UserContext.class));
  }

  @Test
  void mapsEveryResponseFieldFromPagedRepositoryResult() {
    WorklistEntity worklist = completeWorklist(101L, WORK_START);
    when(searchRepository.search(
        any(MyTodoRequest.class),
        eq(0),
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
    request.setPageNo(2);
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
}
