package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import org.uengine.five.repository.WorklistRepository;

class WorkItemAssignmentStateServiceTest {

    private WorkItemAssignmentStateService service;
    private WorklistRepository worklistRepository;
    private ProcessInstanceRepository processInstanceRepository;

    @BeforeEach
    void setUp() {
        service = new WorkItemAssignmentStateService();
        worklistRepository = mock(WorklistRepository.class);
        processInstanceRepository = mock(ProcessInstanceRepository.class);
        service.worklistRepository = worklistRepository;
        service.processInstanceRepository = processInstanceRepository;
    }

    @Test
    void preservesPreviousWorkitemAssignment() {
        WorklistEntity workitem = workitem(1L, "hong", "Hong", "ORG1");

        service.rememberPrevious(workitem);

        assertEquals("hong", workitem.getPrevEndpoint());
        assertEquals("Hong", workitem.getPrevUserName());
        assertEquals("ORG1", workitem.getPrevGroupCd());
    }

    @Test
    void doesNotClearExistingPreviousAssignmentWhenClaimingUnassignedWorkitem() {
        WorklistEntity workitem = workitem(1L, null, null, "ORG1");
        workitem.setPrevEndpoint("hong");
        workitem.setPrevUserName("Hong");
        workitem.setPrevGroupCd("ORG1");

        service.rememberPrevious(workitem);

        assertEquals("hong", workitem.getPrevEndpoint());
        assertEquals("Hong", workitem.getPrevUserName());
        assertEquals("ORG1", workitem.getPrevGroupCd());
    }

    @Test
    void doesNotOverwritePreviousWorkitemAssignmentWhenNothingChanges() {
        WorklistEntity workitem = workitem(1L, "hong", "Hong", "ORG1");
        workitem.setPrevEndpoint("lee");
        workitem.setPrevUserName("Lee");
        workitem.setPrevGroupCd("ORG0");

        service.rememberPreviousIfChanged(workitem, "hong", "Hong", "ORG1");

        assertEquals("lee", workitem.getPrevEndpoint());
        assertEquals("Lee", workitem.getPrevUserName());
        assertEquals("ORG0", workitem.getPrevGroupCd());
    }

    @Test
    void reassignsEveryActiveWorkitemInTheSameLaneAndPreservesPreviousAssignments() {
        WorklistEntity first = workitem(1L, "hong", "Hong", "ORG1");
        WorklistEntity second = workitem(2L, "lee", "Lee", "ORG1");
        when(worklistRepository.findActiveLaneForUpdate(10L, "worker"))
                .thenReturn(List.of(first, second));
        when(worklistRepository.saveAll(List.of(first, second)))
                .thenReturn(List.of(first, second));

        List<WorklistEntity> changed = service.reassignActiveLane(
                10L, "worker", "kim", "Kim", null);

        assertEquals(2, changed.size());
        assertEquals("kim", first.getEndpoint());
        assertEquals("hong", first.getPrevEndpoint());
        assertEquals("Hong", first.getPrevUserName());
        assertEquals("ORG1", first.getPrevGroupCd());
        assertEquals("kim", second.getEndpoint());
        assertEquals("lee", second.getPrevEndpoint());
        assertEquals("Lee", second.getPrevUserName());
        assertEquals("ORG1", second.getPrevGroupCd());
        verify(worklistRepository).saveAll(List.of(first, second));
    }

    @Test
    void aggregatesDistinctActiveHandlersWithoutMovingPreviousUnitHandler() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setCurrEp("hong");
        instance.setCurrRsNm("Hong");
        instance.setCurrGroupCd("ORG1");
        instance.setPrevCurrEp("lee");
        instance.setPrevCurrRsNm("Lee");
        instance.setPrevCurrGroupCd("ORG0");
        when(processInstanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        when(worklistRepository.findActiveByRootOrInstance(10L)).thenReturn(List.of(
                workitem(1L, "hong", "Hong", "ORG1"),
                workitem(2L, "kim", "Kim", "ORG2"),
                workitem(3L, "hong", "Hong", "ORG1"),
                workitem(4L, null, null, "ORG3")));

        WorkItemAssignmentStateService.AssignmentChangeContext context = service.begin(10L);
        service.finish(context);

        assertEquals("hong;kim", instance.getCurrEp());
        assertEquals("Hong;Kim", instance.getCurrRsNm());
        assertEquals("ORG1;ORG2;ORG3", instance.getCurrGroupCd());
        assertEquals("lee", instance.getPrevCurrEp());
        assertEquals("Lee", instance.getPrevCurrRsNm());
        assertEquals("ORG0", instance.getPrevCurrGroupCd());
        verify(processInstanceRepository).save(instance);
    }

    @Test
    void doesNotShiftPreviousValuesWhenAggregateIsUnchanged() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setCurrEp("hong;kim");
        instance.setCurrRsNm("Hong;Kim");
        instance.setCurrGroupCd("ORG1;ORG2");
        instance.setPrevCurrEp("lee");
        when(processInstanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        when(worklistRepository.findActiveByRootOrInstance(10L)).thenReturn(List.of(
                workitem(1L, "hong", "Hong", "ORG1"),
                workitem(2L, "kim", "Kim", "ORG2")));

        service.finish(service.begin(10L));

        assertEquals("lee", instance.getPrevCurrEp());
        verify(processInstanceRepository, never()).save(instance);
    }

    @Test
    void clearsCurrentAggregateWhenNoActiveAssignmentRemains() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setCurrEp("hong");
        instance.setPrevCurrEp("lee");
        when(processInstanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        when(worklistRepository.findActiveByRootOrInstance(10L)).thenReturn(List.of());

        service.finish(service.begin(10L));

        assertNull(instance.getCurrEp());
        assertEquals("lee", instance.getPrevCurrEp());
    }

    @Test
    void completionMovesCompletedWorkitemHandlerToPreviousCurrent() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        when(processInstanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        when(worklistRepository.findActiveByRootOrInstance(10L)).thenReturn(List.of(
                workitem(2L, "kim", "Kim", "ORG2")));
        WorklistEntity completed = workitem(1L, "hong", "Hong", "ORG1");
        completed.setInstId(10L);
        completed.setRootInstId(10L);

        service.completeUnitWork(completed);

        assertEquals("hong", instance.getPrevCurrEp());
        assertEquals("Hong", instance.getPrevCurrRsNm());
        assertEquals("ORG1", instance.getPrevCurrGroupCd());
        assertEquals("kim", instance.getCurrEp());
        assertEquals("Kim", instance.getCurrRsNm());
        assertEquals("ORG2", instance.getCurrGroupCd());
    }

    @Test
    void initializesFirstUnitGroupOnlyWorkitemWithoutEndpoint() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        when(processInstanceRepository.findById(10L)).thenReturn(Optional.of(instance));
        WorklistEntity first = workitem(1L, null, null, "ORG1");
        first.setInstId(10L);
        first.setRootInstId(10L);

        service.initializeFirstUnitHandler(first);

        assertNull(instance.getInitEp());
        assertNull(instance.getInitRsNm());
        assertEquals("ORG1", instance.getInitGroupCd());
    }

    private static WorklistEntity workitem(Long taskId, String endpoint, String resName, String groupCd) {
        WorklistEntity workitem = new WorklistEntity();
        workitem.setTaskId(taskId);
        workitem.setInstId(10L);
        workitem.setRootInstId(10L);
        workitem.setStatus("NEW");
        workitem.setEndpoint(endpoint);
        workitem.setResName(resName);
        workitem.setGroupCd(groupCd);
        return workitem;
    }
}
