package org.uengine.hwlife.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequest;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyRequestItem;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponse;
import org.uengine.hwlife.search.dto.RunningWorkByCorrKeyResponseItem;
import org.uengine.kernel.Activity;

class InstanceIntegrationServiceImplTest {

  private ProcessInstanceRepository processInstanceRepository;
  private WorklistRepository worklistRepository;
  private InstanceIntegrationServiceImpl service;

  @BeforeEach
  void setUp() {
    processInstanceRepository = mock(ProcessInstanceRepository.class);
    worklistRepository = mock(WorklistRepository.class);
    service = new InstanceIntegrationServiceImpl(
        mock(InstanceServiceImpl.class),
        processInstanceRepository,
        worklistRepository);
  }

  @Test
  void returnsEmptyResponseWithoutQueriesForEmptyRequest() {
    RunningWorkByCorrKeyResponse response =
        service.searchRunningWorkByCorrKey(new RunningWorkByCorrKeyRequest());

    assertEquals(List.of(), response.getBswrList());
    verify(processInstanceRepository, never()).findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING));
    verify(worklistRepository, never()).findCurrentWorkItemsByRootInstIds(anyCollection());
  }

  @Test
  void keepsRepositoryCallsConstantForOneHundredCorrKeys() {
    List<RunningWorkByCorrKeyRequestItem> requestItems = new ArrayList<>();
    List<ProcessInstanceEntity> instances = new ArrayList<>();
    List<WorklistEntity> workItems = new ArrayList<>();
    for (int index = 1; index <= 100; index++) {
      String corrKey = "CORR-" + index;
      requestItems.add(requestItem(corrKey));
      instances.add(instance((long) index, corrKey));
      workItems.add(workItem((long) index, (long) index, "TASK-" + index));
    }
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(instances);
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection())).thenReturn(workItems);

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(request(requestItems));

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
  void preservesInputOrderDuplicatesMultipleInstancesAndParallelWorkItems() {
    ProcessInstanceEntity first = instance(11L, "CORR-A");
    ProcessInstanceEntity second = instance(12L, "CORR-A");
    WorklistEntity firstTask = workItem(11L, 101L, "A-1");
    WorklistEntity parallelTask = workItem(11L, 102L, "A-2");
    WorklistEntity secondTask = workItem(12L, 103L, "A-3");
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(List.of(first, second));
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection()))
        .thenReturn(List.of(firstTask, parallelTask, secondTask));

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(request(List.of(
        requestItem("CORR-A"),
        requestItem("UNKNOWN"),
        requestItem("CORR-A"))));

    assertEquals(
        Arrays.asList("A-1", "A-2", "A-3", null, "A-1", "A-2", "A-3"),
        response.getBswrList().stream()
            .map(RunningWorkByCorrKeyResponseItem::getFncgBpmTaskTrcgNm)
            .toList());
    assertEquals(
        "No running BPM instance found for loanPcesMgmtNo=UNKNOWN",
        response.getBswrList().get(3).getPrcsrsltCntn());

    ArgumentCaptor<Collection<String>> corrKeys = collectionCaptor();
    verify(processInstanceRepository).findByCorrKeyInAndStatus(
        corrKeys.capture(), eq(Activity.STATUS_RUNNING));
    assertEquals(List.of("CORR-A", "UNKNOWN"), new ArrayList<>(corrKeys.getValue()));
    verify(worklistRepository).findCurrentWorkItemsByRootInstIds(anyCollection());
  }

  @Test
  void reportsInvalidKeyAndRunningInstanceWithoutActiveWork() {
    ProcessInstanceEntity instance = instance(21L, "CORR-NO-WORK");
    when(processInstanceRepository.findByCorrKeyInAndStatus(
        anyCollection(), eq(Activity.STATUS_RUNNING))).thenReturn(List.of(instance));
    when(worklistRepository.findCurrentWorkItemsByRootInstIds(anyCollection())).thenReturn(List.of());

    RunningWorkByCorrKeyResponse response = service.searchRunningWorkByCorrKey(request(List.of(
        requestItem("   "),
        requestItem("CORR-NO-WORK"))));

    assertEquals(2, response.getBswrList().size());
    assertNull(response.getBswrList().get(0).getLoanPcesMgmtNo());
    assertEquals("loanPcesMgmtNo is required", response.getBswrList().get(0).getPrcsrsltCntn());
    assertEquals(
        "No active work item found for running BPM instance instId=21",
        response.getBswrList().get(1).getPrcsrsltCntn());
  }

  private static RunningWorkByCorrKeyRequest request(
      List<RunningWorkByCorrKeyRequestItem> requestItems) {
    RunningWorkByCorrKeyRequest request = new RunningWorkByCorrKeyRequest();
    request.setBswrList(requestItems);
    return request;
  }

  private static RunningWorkByCorrKeyRequestItem requestItem(String corrKey) {
    RunningWorkByCorrKeyRequestItem item = new RunningWorkByCorrKeyRequestItem();
    item.setLoanPcesMgmtNo(corrKey);
    return item;
  }

  private static ProcessInstanceEntity instance(Long instId, String corrKey) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instId);
    instance.setRootInstId(instId);
    instance.setCorrKey(corrKey);
    instance.setStatus(Activity.STATUS_RUNNING);
    return instance;
  }

  private static WorklistEntity workItem(Long rootInstId, Long taskId, String tracingTag) {
    WorklistEntity workItem = new WorklistEntity();
    workItem.setRootInstId(rootInstId);
    workItem.setTaskId(taskId);
    workItem.setTrcTag(tracingTag);
    workItem.setStatus("NEW");
    return workItem;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> ArgumentCaptor<Collection<T>> collectionCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
  }
}
