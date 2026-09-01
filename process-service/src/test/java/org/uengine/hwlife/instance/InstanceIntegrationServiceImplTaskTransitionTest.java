package org.uengine.hwlife.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.esbclient.dto.EsbCodes;
import org.uengine.hwlife.instance.dto.TaskReturnRequest;
import org.uengine.hwlife.instance.dto.TaskReturnResponse;
import org.uengine.hwlife.instance.dto.TaskSkipRequest;
import org.uengine.hwlife.instance.dto.TaskSkipResponse;
import org.uengine.kernel.Activity;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.HumanActivity;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;

class InstanceIntegrationServiceImplTaskTransitionTest {

  private InstanceServiceImpl instanceService;
  private WorklistRepository worklistRepository;
  private InstanceIntegrationServiceImpl service;

  @BeforeEach
  void setUp() {
    instanceService = mock(InstanceServiceImpl.class);
    worklistRepository = mock(WorklistRepository.class);
    service = new InstanceIntegrationServiceImpl(
        instanceService,
        worklistRepository,
        mock(EsbClient.class),
        mock(BulkAssignItemService.class));
  }

  @AfterEach
  void tearDown() {
    UserContext.getThreadLocalInstance().setUserId(null);
    GlobalContext.setUserId(null);
  }

  @Test
  void skipUsesEnginePrimitiveWithoutCallingUiTaskApi() throws Exception {
    WorklistEntity workitem = workitem(101L, 200L, "hong", "RUNNING");
    workitem.setTrcTag("current-task");
    when(worklistRepository.findById(101L)).thenReturn(Optional.of(workitem));
    ProcessInstance instance = mock(ProcessInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    HumanActivity currentActivity = mock(HumanActivity.class);
    Activity nextActivity = mock(Activity.class);
    when(instanceService.getProcessInstanceLocal("200")).thenReturn(instance);
    when(instance.getProcessDefinition()).thenReturn(definition);
    when(definition.getActivity("current-task")).thenReturn(currentActivity);
    when(definition.getChildActivities()).thenReturn(List.of(currentActivity));
    when(currentActivity.getTracingTag()).thenReturn("current-task");
    when(currentActivity.getStatus(instance)).thenReturn(Activity.STATUS_RUNNING);
    when(currentActivity.isNotificationWorkitem()).thenReturn(false);
    when(currentActivity.getPossibleNextActivities(instance, null)).thenReturn(List.of(nextActivity));
    when(instance.isRunning("current-task")).thenReturn(true);

    TaskSkipRequest request = new TaskSkipRequest();
    request.setHndrEmnb("hong");
    request.setFncgBpmTaskLstId("101");

    TaskSkipResponse response = service.skipWorklist(request);

    assertEquals(EsbCodes.MSGE_CODE_SUCCESS, response.getPrcsRsltCntn());
    verify(definition).flowControl("skip", instance, "current-task");
    verify(instanceService, never()).skipWorkItem(any(), any());
  }

  @Test
  void skipRejectsHandlerMismatchBeforeCallingEngine() throws Exception {
    when(worklistRepository.findById(101L))
        .thenReturn(Optional.of(workitem(101L, 200L, "kim", "RUNNING")));
    TaskSkipRequest request = new TaskSkipRequest();
    request.setHndrEmnb("hong");
    request.setFncgBpmTaskLstId("101");

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class, () -> service.skipWorklist(request));

    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verify(instanceService, never()).getProcessInstanceLocal(any());
  }

  @Test
  void returnUsesBackToHereWithoutCallingUiTaskApi() throws Exception {
    WorklistEntity current = workitem(101L, 200L, "hong", "RUNNING");
    current.setTrcTag("current-task");
    when(worklistRepository.findActiveByRootOrInstance(200L)).thenReturn(List.of(current));
    when(worklistRepository.findByInstIdAndStatusIn(200L, List.of("NEW", "RUNNING")))
        .thenReturn(List.of(current));

    ProcessInstance instance = mock(ProcessInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    HumanActivity currentActivity = mock(HumanActivity.class);
    HumanActivity previousActivity = mock(HumanActivity.class);
    Vector<Activity> previous = new Vector<>();
    previous.add(previousActivity);
    when(instanceService.getProcessInstanceLocal("200")).thenReturn(instance);
    when(instance.getProcessDefinition()).thenReturn(definition);
    when(definition.getActivity("current-task")).thenReturn(currentActivity);
    when(currentActivity.getTracingTag()).thenReturn("current-task");
    when(currentActivity.isNotificationWorkitem()).thenReturn(false);
    when(currentActivity.getIncomingSequenceFlows()).thenReturn(List.of());
    when(currentActivity.getPreviousActivities()).thenReturn(previous);
    when(previousActivity.getTracingTag()).thenReturn("previous-task");
    when(instance.isRunning("current-task")).thenReturn(true);

    WorklistEntity completed = workitem(90L, 200L, "kim", "COMPLETED");
    completed.setTrcTag("previous-task");
    when(worklistRepository.findHistoryByRootOrInstance(200L)).thenReturn(List.of(completed));

    TaskReturnRequest request = returnRequest("hong", "200", "previous-task");
    TaskReturnResponse response = service.returnToPrevious(request);

    assertEquals(EsbCodes.MSGE_CODE_SUCCESS, response.getPrcsRsltCntn());
    assertEquals("SUSPENDED", current.getStatus());
    verify(previousActivity).backToHere(instance);
    verify(instanceService, never()).getTaskReturnAvailability(any());
    verify(instanceService, never()).returnWorkItem(any(), any());
  }

  @Test
  void returnRejectsHandlerWithoutOwnedActiveWorkItem() throws Exception {
    WorklistEntity current = workitem(101L, 200L, "kim", "RUNNING");
    when(worklistRepository.findActiveByRootOrInstance(200L)).thenReturn(List.of(current));
    when(worklistRepository.findByInstIdAndStatusIn(200L, List.of("NEW", "RUNNING")))
        .thenReturn(List.of(current));

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> service.returnToPrevious(returnRequest("hong", "200", "previous-task")));

    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verify(instanceService, never()).getProcessInstanceLocal(any());
  }

  @Test
  void returnRejectsTracingTagThatIsNotPreviousCompletedTask() throws Exception {
    WorklistEntity current = workitem(101L, 200L, "hong", "RUNNING");
    current.setTrcTag("current-task");
    when(worklistRepository.findActiveByRootOrInstance(200L)).thenReturn(List.of(current));
    when(worklistRepository.findByInstIdAndStatusIn(200L, List.of("NEW", "RUNNING")))
        .thenReturn(List.of(current));
    ProcessInstance instance = mock(ProcessInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    HumanActivity currentActivity = mock(HumanActivity.class);
    when(instanceService.getProcessInstanceLocal("200")).thenReturn(instance);
    when(instance.getProcessDefinition()).thenReturn(definition);
    when(definition.getActivity("current-task")).thenReturn(currentActivity);
    when(currentActivity.getTracingTag()).thenReturn("current-task");
    when(currentActivity.isNotificationWorkitem()).thenReturn(false);
    when(currentActivity.getIncomingSequenceFlows()).thenReturn(List.of());
    when(currentActivity.getPreviousActivities()).thenReturn(new Vector<>());
    when(instance.isRunning("current-task")).thenReturn(true);

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> service.returnToPrevious(returnRequest("hong", "200", "not-previous")));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    verify(instanceService, never()).getTaskReturnAvailability(any());
    verify(instanceService, never()).returnWorkItem(any(), any());
  }

  private static TaskReturnRequest returnRequest(String handler, String instanceId, String tracingTag) {
    TaskReturnRequest request = new TaskReturnRequest();
    request.setHndrEmnb(handler);
    request.setFncgBpmPcesIntcId(instanceId);
    request.setFncgBpmTaskTrcgNm(tracingTag);
    return request;
  }

  private static WorklistEntity workitem(Long taskId, Long instanceId, String endpoint, String status) {
    WorklistEntity workitem = new WorklistEntity();
    workitem.setTaskId(taskId);
    workitem.setInstId(instanceId);
    workitem.setRootInstId(instanceId);
    workitem.setEndpoint(endpoint);
    workitem.setStatus(status);
    return workitem;
  }
}
