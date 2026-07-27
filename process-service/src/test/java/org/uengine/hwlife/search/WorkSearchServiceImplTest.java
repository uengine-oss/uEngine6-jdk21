package org.uengine.hwlife.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.hwlife.search.dto.OrgRunningRequest;
import org.uengine.hwlife.search.dto.OrgRunningResponse;

class WorkSearchServiceImplTest {

  private final WorklistRepository worklistRepository = mock(WorklistRepository.class);
  private final WorkSearchServiceImpl service = new WorkSearchServiceImpl(worklistRepository);

  @Test
  void searchOrgRunningFiltersRunningItemsAtInclusiveDateBoundariesAndMapsDto() {
    LocalDateTime boundary = LocalDateTime.of(2026, 8, 3, 9, 0);
    WorklistEntity matching = worklist(10L, "RUNNING", boundary, "ORG-1", "INIT-1");
    WorklistEntity wrongCustomer = worklist(11L, "NEW", boundary, "ORG-1", "INIT-1");
    wrongCustomer.getProcessInstance().setCustId("OTHER");
    WorklistEntity completed = worklist(12L, "COMPLETED", boundary, "ORG-1", "INIT-1");
    when(worklistRepository.findRunningForOrgSearch()).thenReturn(List.of(matching, wrongCustomer, completed));

    OrgRunningRequest request = new OrgRunningRequest();
    request.setCustId("CUST-1");
    request.setFncgWndwOrgnCode("ORG-1");
    request.setRqstDvsnCode("N");
    request.setRqstStarDttm(boundary);
    request.setRqstEndDttm(boundary);

    OrgRunningResponse response = service.searchOrgRunning(request);

    assertThat(response.getTotCont()).isEqualTo(1);
    assertThat(response.getOrgnPrgslist()).singleElement().satisfies(item -> {
      assertThat(item.getLoanPcesMgmtNo()).isEqualTo("CORR-10");
      assertThat(item.getHndrOrgnCode()).isEqualTo("ORG-1");
      assertThat(item.getFncgBpmTaskTrcgNm()).isEqualTo("TASK-10");
      assertThat(item.getUworStarDttm()).isEqualTo(boundary);
      assertThat(item.getFncgBpmtaskLstId()).isEqualTo("10");
    });
  }

  @Test
  void searchOrgRunningUsesRequestOrganizationWhenRequestDivisionIsY() {
    WorklistEntity requestOrganization = worklist(20L, "NEW", LocalDateTime.of(2026, 8, 3, 9, 0), "ORG-2", "REQUEST-ORG");
    WorklistEntity processingOrganization = worklist(21L, "NEW", LocalDateTime.of(2026, 8, 3, 10, 0), "REQUEST-ORG", "OTHER-ORG");
    when(worklistRepository.findRunningForOrgSearch()).thenReturn(List.of(requestOrganization, processingOrganization));

    OrgRunningRequest request = new OrgRunningRequest();
    request.setFncgWndwOrgnCode("REQUEST-ORG");
    request.setRqstDvsnCode("Y");

    OrgRunningResponse response = service.searchOrgRunning(request);

    assertThat(response.getTotCont()).isEqualTo(1);
    assertThat(response.getOrgnPrgslist()).singleElement()
        .extracting(item -> item.getFncgBpmtaskLstId())
        .isEqualTo("20");
  }

  @Test
  void searchOrgRunningNormalizesInvalidPageNumbersAndPaginates() {
    List<WorklistEntity> worklists = new ArrayList<>();
    LocalDateTime start = LocalDateTime.of(2026, 8, 3, 9, 0);
    for (long taskId = 1; taskId <= 21; taskId++) {
      worklists.add(worklist(taskId, "NEW", start.plusMinutes(taskId), "ORG-1", "INIT-1"));
    }
    when(worklistRepository.findRunningForOrgSearch()).thenReturn(worklists);

    OrgRunningRequest firstPageRequest = new OrgRunningRequest();
    firstPageRequest.setPageNo(0);
    OrgRunningResponse firstPage = service.searchOrgRunning(firstPageRequest);

    OrgRunningRequest secondPageRequest = new OrgRunningRequest();
    secondPageRequest.setPageNo(2);
    OrgRunningResponse secondPage = service.searchOrgRunning(secondPageRequest);

    assertThat(firstPage.getTotCont()).isEqualTo(21);
    assertThat(firstPage.getOrgnPrgslist()).hasSize(20);
    assertThat(firstPage.getOrgnPrgslist().get(0).getFncgBpmtaskLstId()).isEqualTo("21");
    assertThat(secondPage.getOrgnPrgslist()).singleElement()
        .extracting(item -> item.getFncgBpmtaskLstId())
        .isEqualTo("1");
  }

  private static WorklistEntity worklist(
      long taskId, String status, LocalDateTime start, String assignGroup, String initComCd) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(taskId + 1000);
    instance.setStartedDate(asDate(start.minusHours(1)));
    instance.setBsnsClsfCode("BSWR-1");
    instance.setFncgBswrDvsnCode("FNCG-1");
    instance.setLoanCntcNo("LOAN-1");
    instance.setFncgSuptTrgtDvsnCode("SUPPORT-1");
    instance.setLoanSubjDvsnCode("SUBJECT-1");
    instance.setCustId("CUST-1");
    instance.setFncgMneyUsagClsfCode("MONEY-1");
    instance.setCorrKey("CORR-" + taskId);
    instance.setInitEp("INIT-EMP");
    instance.setInitComCd(initComCd);

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(taskId + 1000);
    worklist.setProcessInstance(instance);
    worklist.setStatus(status);
    worklist.setStartDate(asDate(start));
    worklist.setAssignGroup(assignGroup);
    worklist.setEndpoint("HANDLER-1");
    worklist.setTitle("Task " + taskId);
    worklist.setTrcTag("TASK-" + taskId);
    return worklist;
  }

  private static Date asDate(LocalDateTime value) {
    return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
  }
}
