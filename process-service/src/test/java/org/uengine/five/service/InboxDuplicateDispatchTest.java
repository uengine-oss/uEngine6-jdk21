package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.EventMappingEntity;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.messaging.NonRetryableInboxException;
import org.uengine.five.repository.EventMappingRepository;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

class InboxDuplicateDispatchTest {
    private AsyncEventListener listener;
    @BeforeEach void setup() {
        listener = new AsyncEventListener();
        listener.eventMappingRepository = mock(EventMappingRepository.class);
        listener.processInstanceRepository = mock(ProcessInstanceRepository.class);
        listener.worklistRepository = mock(WorklistRepository.class);
        listener.instanceService = mock(InstanceService.class);
        listener.instanceServiceImpl = mock(InstanceServiceImpl.class);
    }
    private EventMappingEntity mapping(String definition, boolean start) {
        EventMappingEntity m = new EventMappingEntity();
        m.setDefinitionId(definition); m.setIsStartEvent(start);
        m.setEventName("EVENT"); m.setTracingTag("A"); m.setCorrelationKey("corr"); return m;
    }
    private ProcessInstanceEntity instance(String definition, String status) {
        ProcessInstanceEntity p = new ProcessInstanceEntity();
        p.setInstId(1L); p.setDefId(definition); p.setStatus(status); return p;
    }
    private void dispatch() { listener.wheneverEvent("{\"corr\":\"KEY\"}", "EVENT", "KEY", "hong"); }
    @Test void rejectsDuplicateStartInEveryState() {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("EVENT"))
                .thenReturn(List.of(mapping("loan.bpmn", true)));
        for (String status : List.of("Running", "Completed", "Stopped", "Compensated", "Fault")) {
            when(listener.processInstanceRepository.findByCorrKey("KEY"))
                    .thenReturn(List.of(instance("loan", status)));
            RuntimeException e = assertThrows(RuntimeException.class, this::dispatch);
            assertTrue(assertInstanceOf(NonRetryableInboxException.class, e.getCause()).isDuplicate());
        }
        verifyNoInteractions(listener.instanceService);
    }
    @Test void sameCorrelationDoesNotBlockDifferentDefinition() throws Exception {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("EVENT"))
                .thenReturn(List.of(mapping("new.bpmn", true)));
        when(listener.processInstanceRepository.findByCorrKey("KEY"))
                .thenReturn(List.of(instance("old", "Completed")));
        assertDoesNotThrow(this::dispatch);
        verify(listener.instanceService).start(any());
    }
    @Test void skippedStartDoesNotHideAnotherSuccessfulMapping() throws Exception {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("EVENT"))
                .thenReturn(List.of(mapping("old", true), mapping("new", true)));
        when(listener.processInstanceRepository.findByCorrKey("KEY"))
                .thenReturn(List.of(instance("old", "Completed")));
        assertDoesNotThrow(this::dispatch);
        verify(listener.instanceService, times(1)).start(any());
    }
    @Test void previouslyCompletedMappedWorkMakesNoOpADuplicate() {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("EVENT"))
                .thenReturn(List.of(mapping("loan.bpmn", false)));
        when(listener.processInstanceRepository.findByCorrKey("KEY"))
                .thenReturn(List.of(instance("loan", "Completed")));
        WorklistEntity task = new WorklistEntity(); task.setTrcTag("A"); task.setStatus("COMPLETED");
        when(listener.worklistRepository.findByInstIdAndStatusIn(1L, List.of("COMPLETED")))
                .thenReturn(List.of(task));
        RuntimeException e = assertThrows(RuntimeException.class, this::dispatch);
        assertTrue(assertInstanceOf(NonRetryableInboxException.class, e.getCause()).isDuplicate());
    }
    @Test void anotherActivityHistoryIsNotLabeledDuplicate() {
        when(listener.eventMappingRepository.findAllByEventNameOrderByIdAsc("EVENT"))
                .thenReturn(List.of(mapping("loan", false)));
        when(listener.processInstanceRepository.findByCorrKey("KEY"))
                .thenReturn(List.of(instance("loan", "Completed")));
        WorklistEntity task = new WorklistEntity(); task.setTrcTag("B"); task.setStatus("COMPLETED");
        when(listener.worklistRepository.findByInstIdAndStatusIn(1L, List.of("COMPLETED")))
                .thenReturn(List.of(task));
        assertDoesNotThrow(this::dispatch);
    }
}
