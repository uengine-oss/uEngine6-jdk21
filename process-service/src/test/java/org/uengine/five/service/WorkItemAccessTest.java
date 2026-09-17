package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

class WorkItemAccessTest {
    private final WorklistEntity work = new WorklistEntity();
    private final org.uengine.kernel.ProcessInstance instance = mock(org.uengine.kernel.ProcessInstance.class);
    private final InstanceServiceImpl service = new InstanceServiceImpl() {
        @Override public org.uengine.kernel.ProcessInstance getProcessInstanceLocal(String id) { return instance; }
    };
    WorkItemAccessTest() {
        UserContext user = UserContext.getThreadLocalInstance();
        user.setUserId("alice"); user.setGroups(List.of("A")); user.setScopes(List.of("X"));
        work.setGroupCd("A"); work.setScope("Y"); work.setDispatchOption(1);
        service.worklistRepository = mock(WorklistRepository.class);
        service.assignmentStateService = mock(WorkItemAssignmentStateService.class);
        when(service.worklistRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(work));
        when(service.worklistRepository.findById(1L)).thenReturn(Optional.of(work));
    }
    @AfterEach void clear() {
        UserContext user = UserContext.getThreadLocalInstance();
        user.setUserId(null); user.setGroups(null); user.setScopes(null);
    }
    @Test void matchingGroupDoesNotBypassScopeForClaimReadOrSave() {
        RoleMappingCommand command = new RoleMappingCommand(); command.setEndpoint("alice");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.claimWorkItem("1", command)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.getWorkItem("1")).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.putWorkItem("1", null)).getStatusCode().value());
        assertNull(work.getEndpoint());
        verify(service.worklistRepository, never()).save(any());
        verifyNoInteractions(service.assignmentStateService);
    }
    @Test void cannotImpersonateAnotherClaimant() {
        work.setScope("X");
        RoleMappingCommand command = new RoleMappingCommand(); command.setEndpoint("bob");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.claimWorkItem("1", command)).getStatusCode().value());
        assertNull(work.getEndpoint());
        verifyNoInteractions(service.assignmentStateService);
    }
    @Test void missingGroupDoesNotBypassRoleAndMissingRoleDoesNotBypassGroup() {
        work.setGroupCd(null); work.setScope("X");
        assertDoesNotThrow(() -> WorkItemAccess.requireVisible(work, "alice"));
        work.setScope("Y"); assertThrows(ResponseStatusException.class, () -> WorkItemAccess.requireVisible(work, "alice"));
        work.setGroupCd("A"); work.setScope(null);
        assertDoesNotThrow(() -> WorkItemAccess.requireVisible(work, "alice"));
        work.setGroupCd("B"); assertThrows(ResponseStatusException.class, () -> WorkItemAccess.requireVisible(work, "alice"));
    }
    @Test void assignedOwnerIsRetainedAndOtherUserCannotComplete() {
        work.setEndpoint("alice");
        assertDoesNotThrow(() -> WorkItemAccess.requireVisible(work, "alice"));
        assertDoesNotThrow(() -> InstanceServiceImpl.validateCompletionOwner(work, "alice"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> InstanceServiceImpl.validateCompletionOwner(work, "bob")).getStatusCode().value());
    }
    @Test void responseDoesNotSerializeOrMutateEntityCycle() throws Exception {
        org.uengine.five.entity.ProcessInstanceEntity instance = new org.uengine.five.entity.ProcessInstanceEntity();
        instance.setWorkLists(List.of(work)); work.setProcessInstance(instance);
        work.setTaskId(1L); work.setTitle("Task");
        WorklistEntity response = InstanceServiceImpl.worklistResponse(work);
        assertNull(response.getProcessInstance()); assertSame(instance, work.getProcessInstance());
        assertEquals("Task", response.getTitle());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("\"taskId\":1"));
    }

    @Test void ownerCanReadTaskWithoutEventSynchronization() throws Exception {
        work.setEndpoint("alice"); work.setInstId(10L); work.setDefId("test");
        work.setDefVerId("0.1"); work.setTrcTag("Task_2");
        service.definitionService = mock(DefinitionServiceUtil.class);
        org.uengine.kernel.ProcessDefinition definition = mock(org.uengine.kernel.ProcessDefinition.class);
        org.uengine.kernel.HumanActivity activity = mock(org.uengine.kernel.HumanActivity.class);
        when(service.definitionService.getDefinition("test", "0.1")).thenReturn(definition);
        when(definition.getActivity("Task_2")).thenReturn(activity);
        when(activity.getStatus(instance)).thenReturn("Running");
        assertNotNull(service.getWorkItem("1"));
        verify(activity, never()).getMappingInValues(any());
    }
}
