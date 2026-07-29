package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.uengine.five.dto.ProcessExecutionCommand;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.five.repository.ProcessInstanceRepository;

class AsyncEventListenerMultiMappingTest {

    private AsyncEventListener listener;
    private InstanceService instanceService;
    private ProcessInstanceRepository processInstanceRepository;

    @BeforeEach
    void setUp() {
        listener = new AsyncEventListener();
        listener.eventMappingRepository = mock(EventMappingRepository.class);
        listener.processInstanceRepository = mock(ProcessInstanceRepository.class);
        listener.instanceService = mock(InstanceService.class);
        listener.instanceServiceImpl = mock(InstanceServiceImpl.class);
        instanceService = listener.instanceService;
        processInstanceRepository = listener.processInstanceRepository;
    }

    @Test
    void startsEveryDistinctDefinitionForSharedEventName() throws Exception {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("LOAN_RECEIVED"))
                .thenReturn(List.of(
                        mapping(1L, "definitions/loan-a.bpmn", "start-a"),
                        mapping(2L, "definitions/loan-b.bpmn", "start-b"),
                        mapping(3L, "definitions/loan-a.bpmn", "start-a-duplicate-route")));
        when(processInstanceRepository.findByCorrKeyAndStatus("LP-100", "Running"))
                .thenReturn(List.of());

        listener.wheneverEvent(
                "{\"loanPcesMgmtNo\":\"LP-100\",\"custId\":\"C-1\"}",
                "LOAN_RECEIVED",
                "fallback");

        ArgumentCaptor<ProcessExecutionCommand> command =
                ArgumentCaptor.forClass(ProcessExecutionCommand.class);
        verify(instanceService, times(2)).start(command.capture());
        Set<String> definitions = command.getAllValues().stream()
                .map(ProcessExecutionCommand::getProcessDefinitionId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("definitions/loan-a.bpmn", "definitions/loan-b.bpmn"), definitions);
        assertTrue(command.getAllValues().stream()
                .allMatch(value -> "LP-100".equals(value.getCorrelationKeyValue())));
    }

    @Test
    void oneFailedStartDoesNotHideOtherMappedDefinitions() throws Exception {
        EventMappingEntity first = mapping(1L, "definitions/loan-a.bpmn", "start-a");
        EventMappingEntity second = mapping(2L, "definitions/loan-b.bpmn", "start-b");
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("LOAN_RECEIVED"))
                .thenReturn(List.of(first, second));
        when(processInstanceRepository.findByCorrKeyAndStatus("LP-200", "Running"))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("first failed"))
                .doReturn(null)
                .when(instanceService).start(any(ProcessExecutionCommand.class));

        listener.wheneverEvent(
                "{\"loanPcesMgmtNo\":\"LP-200\"}",
                "LOAN_RECEIVED",
                "fallback");

        verify(instanceService, times(2)).start(any(ProcessExecutionCommand.class));
    }

    @Test
    void failsInboxWhenEveryMappedStartFails() throws Exception {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("LOAN_RECEIVED"))
                .thenReturn(List.of(
                        mapping(1L, "definitions/loan-a.bpmn", "start-a"),
                        mapping(2L, "definitions/loan-b.bpmn", "start-b")));
        when(processInstanceRepository.findByCorrKeyAndStatus("LP-300", "Running"))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("start failed"))
                .when(instanceService).start(any(ProcessExecutionCommand.class));

        assertThrows(RuntimeException.class, () -> listener.wheneverEvent(
                "{\"loanPcesMgmtNo\":\"LP-300\"}",
                "LOAN_RECEIVED",
                "fallback"));

        verify(instanceService, times(2)).start(any(ProcessExecutionCommand.class));
    }

    private static EventMappingEntity mapping(Long id, String definitionId, String tracingTag) {
        EventMappingEntity mapping = new EventMappingEntity();
        mapping.setId(id);
        mapping.setEventName("LOAN_RECEIVED");
        mapping.setDefinitionId(definitionId);
        mapping.setTracingTag(tracingTag);
        mapping.setCorrelationKey("loanPcesMgmtNo");
        mapping.setIsStartEvent(true);
        return mapping;
    }
}
