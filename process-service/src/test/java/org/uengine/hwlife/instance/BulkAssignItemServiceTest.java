package org.uengine.hwlife.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.instance.BulkAssignItemService.BulkAssignItemException;
import org.uengine.hwlife.instance.dto.BulkAssignRequestItem;

class BulkAssignItemServiceTest {

  private InstanceServiceImpl instanceService;
  private WorklistRepository worklistRepository;
  private BulkAssignItemService service;

  @BeforeEach
  void setUp() {
    instanceService = mock(InstanceServiceImpl.class);
    worklistRepository = mock(WorklistRepository.class);
    service = new BulkAssignItemService(instanceService, worklistRepository);
  }

  @Test
  void usesIndependentTransactionForEachItem() throws Exception {
    Transactional transactional = BulkAssignItemService.class
        .getMethod("assign", BulkAssignRequestItem.class, String.class, String.class)
        .getAnnotation(Transactional.class);

    assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
  }

  @Test
  void delegatesEligibleAssignmentToExistingClaimFlow() throws Exception {
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(worklist(null, "NEW", 1)));
    ArgumentCaptor<RoleMappingCommand> mapping = ArgumentCaptor.forClass(RoleMappingCommand.class);

    service.assign(item("101", "201", "kim"), "kim", "Kim");

    verify(instanceService).claimWorkItem(eq("101"), mapping.capture());
    assertEquals("kim", mapping.getValue().getEndpoint());
    assertEquals("Kim", mapping.getValue().getResourceName());
  }

  @Test
  void rejectsAlreadyAssignedWorkWithoutOverwritingIt() throws Exception {
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(worklist("hong", "NEW", 1)));

    BulkAssignItemException exception = assertThrows(
        BulkAssignItemException.class,
        () -> service.assign(item("101", "201", "kim"), "kim", "Kim"));

    assertEquals("ALREADY_ASSIGNED", exception.getResultCode());
    verify(instanceService, never()).claimWorkItem(any(), any());
  }

  @Test
  void returnsStableCodeWhenWorkitemDoesNotExist() throws Exception {
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.empty());

    assertCode("WORKITEM_NOT_FOUND", item("101", "201", "kim"));
    verify(instanceService, never()).claimWorkItem(any(), any());
  }

  @Test
  void validatesInstanceStatusAndDispatchOption() throws Exception {
    WorklistEntity mismatched = worklist(null, "NEW", 1);
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(mismatched));
    assertCode("INSTANCE_MISMATCH", item("101", "999", "kim"));

    WorklistEntity completed = worklist(null, "COMPLETED", 1);
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(completed));
    assertCode("WORKITEM_NOT_NEW", item("101", "201", "kim"));

    WorklistEntity direct = worklist(null, "NEW", 0);
    when(worklistRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(direct));
    assertCode("NOT_BULK_ASSIGNABLE", item("101", "201", "kim"));
  }

  private void assertCode(String expected, BulkAssignRequestItem item) {
    BulkAssignItemException exception = assertThrows(
        BulkAssignItemException.class,
        () -> service.assign(item, "kim", "Kim"));
    assertEquals(expected, exception.getResultCode());
  }

  private static WorklistEntity worklist(String endpoint, String status, int dispatchOption) {
    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(101L);
    worklist.setInstId(201L);
    worklist.setRootInstId(201L);
    worklist.setEndpoint(endpoint);
    worklist.setStatus(status);
    worklist.setDispatchOption(dispatchOption);
    return worklist;
  }

  private static BulkAssignRequestItem item(String taskId, String instanceId, String handler) {
    BulkAssignRequestItem item = new BulkAssignRequestItem();
    item.setFncgBpmTaskLstId(taskId);
    item.setFncgBpmPcesIntcId(instanceId);
    item.setHndrEmnb(handler);
    return item;
  }
}
