package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.LoanStatusResponse;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LoanBpmStatusControllerTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private WorklistRepository worklistRepository;

    private LoanBpmStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new LoanBpmStatusController(processInstanceRepository, worklistRepository);
        UserContext.getThreadLocalInstance().setUserId("hong");
        UserContext.getThreadLocalInstance().setGroups(Collections.emptyList());
        UserContext.getThreadLocalInstance().setScopes(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.getThreadLocalInstance().setUserId(null);
        UserContext.getThreadLocalInstance().setGroups(Collections.emptyList());
        UserContext.getThreadLocalInstance().setScopes(Collections.emptyList());
    }

    @Test
    void returnsAllCallerVisibleActiveWorkItemsForRunningCasesWithoutSensitiveFields() throws Exception {
        ProcessInstanceEntity first = instance(11L, "Running");
        ProcessInstanceEntity second = instance(12L, "Running");
        WorklistEntity firstVisible = workItem(101L, 11L, "NEW");
        WorklistEntity secondVisible = workItem(102L, 12L, "RUNNING");
        WorklistEntity hidden = workItem(103L, 11L, "NEW");
        hidden.setPayload("financial source payload");
        hidden.setReason("private reason");

        when(processInstanceRepository.findByCorrKey("LOAN-1")).thenReturn(Arrays.asList(first, second));
        when(worklistRepository.findToDo()).thenReturn(Arrays.asList(firstVisible, secondVisible));
        when(worklistRepository.findCurrentWorkItemByInstId(11L)).thenReturn(Arrays.asList(firstVisible, hidden));
        when(worklistRepository.findCurrentWorkItemByInstId(12L)).thenReturn(Collections.singletonList(secondVisible));

        LoanStatusResponse response = controller.getStatus("LOAN-1", authenticatedRequest());

        assertEquals("RUNNING", response.getProcessStatus());
        assertEquals(2, response.getActiveWorkItems().size());
        String json = new ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains("payload"));
        assertFalse(json.contains("reason"));
        assertFalse(json.contains("financial source payload"));
        assertFalse(json.contains("private reason"));
    }

    @Test
    void returnsCompletedWhenNoRunningOrStoppedCaseExists() {
        when(processInstanceRepository.findByCorrKey("LOAN-2"))
                .thenReturn(Collections.singletonList(instance(21L, "Completed")));

        assertEquals("COMPLETED", controller.getStatus("LOAN-2", authenticatedRequest()).getProcessStatus());
    }

    @Test
    void returnsStoppedBeforeCompletedWhenNoCaseIsRunning() {
        when(processInstanceRepository.findByCorrKey("LOAN-3"))
                .thenReturn(Arrays.asList(instance(31L, "Completed"), instance(32L, "Stopped")));

        assertEquals("STOPPED", controller.getStatus("LOAN-3", authenticatedRequest()).getProcessStatus());
    }

    @Test
    void normalizesSkippedCaseToCompleted() {
        when(processInstanceRepository.findByCorrKey("LOAN-4"))
                .thenReturn(Collections.singletonList(instance(41L, "Skipped")));

        assertEquals("COMPLETED", controller.getStatus("LOAN-4", authenticatedRequest()).getProcessStatus());
    }

    @Test
    void returnsNoCaseWhenNoBpmInstanceExists() {
        when(processInstanceRepository.findByCorrKey("LOAN-5")).thenReturn(Collections.emptyList());

        LoanStatusResponse response = controller.getStatus("LOAN-5", authenticatedRequest());

        assertEquals("NO_CASE", response.getProcessStatus());
        assertEquals(0, response.getActiveWorkItems().size());
    }

    @Test
    void mapsAcceptedButUnprocessedInboxToNoCaseBecauseInboxReceiptIsNotBpmProgress() {
        // The inbox is accepted separately; without a BPM instance this endpoint must not invent a pending status.
        when(processInstanceRepository.findByCorrKey("LOAN-6")).thenReturn(Collections.emptyList());

        LoanStatusResponse response = controller.getStatus("LOAN-6", authenticatedRequest());

        assertEquals("NO_CASE", response.getProcessStatus());
        assertEquals(0, response.getActiveWorkItems().size());
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        UserContext.getThreadLocalInstance().setUserId(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getStatus("LOAN-7", new MockHttpServletRequest()));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        return request;
    }

    private ProcessInstanceEntity instance(Long instId, String status) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setInstId(instId);
        instance.setRootInstId(instId);
        instance.setStatus(status);
        return instance;
    }

    private WorklistEntity workItem(Long taskId, Long instanceId, String status) {
        WorklistEntity workItem = new WorklistEntity();
        workItem.setTaskId(taskId);
        workItem.setInstId(instanceId);
        workItem.setRootInstId(instanceId);
        workItem.setTitle("Loan review");
        workItem.setStatus(status);
        workItem.setRoleName("reviewer");
        workItem.setGroupCd("LOAN");
        workItem.setScope("loan-reviewer");
        return workItem;
    }
}
