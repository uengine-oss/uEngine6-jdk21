package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.dto.WorkItemResource;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

class InstanceServiceImplReassignmentTest {

    @Test
    void preservesCurrentAssignmentBeforeReassigningActiveWorkItem() throws Exception {
        WorklistEntity worklist = workItem("NEW");
        WorklistRepository repository = mock(WorklistRepository.class);
        when(repository.findById(2109L)).thenReturn(Optional.of(worklist));

        InstanceServiceImpl service = spy(new InstanceServiceImpl());
        ReflectionTestUtils.setField(service, "worklistRepository", repository);
        doReturn(new WorkItemResource()).when(service).getWorkItem("2109");

        RoleMappingCommand assignment = new RoleMappingCommand();
        assignment.setEndpoint("kim");
        assignment.setResourceName("kim");

        service.reassignWorkItem("2109", assignment);

        assertEquals("kim", worklist.getEndpoint());
        assertEquals("kim", worklist.getResName());
        assertEquals("hong", worklist.getPrevEndpoint());
        assertEquals("Hong User", worklist.getPrevUserName());
        assertEquals("ORG-OLD", worklist.getPrevGroupCd());
        verify(repository).save(worklist);
    }

    @Test
    void rejectsCompletedWorkItemWithoutChangingAssignment() {
        WorklistEntity worklist = workItem("COMPLETED");
        WorklistRepository repository = mock(WorklistRepository.class);
        when(repository.findById(2109L)).thenReturn(Optional.of(worklist));

        InstanceServiceImpl service = new InstanceServiceImpl();
        ReflectionTestUtils.setField(service, "worklistRepository", repository);

        RoleMappingCommand assignment = new RoleMappingCommand();
        assignment.setEndpoint("kim");

        assertThrows(ResponseStatusException.class,
                () -> service.reassignWorkItem("2109", assignment));
        assertEquals("hong", worklist.getEndpoint());
        verify(repository, never()).save(any());
    }

    private static WorklistEntity workItem(String status) {
        WorklistEntity worklist = new WorklistEntity();
        worklist.setTaskId(2109L);
        worklist.setInstId(1648L);
        worklist.setStatus(status);
        worklist.setEndpoint("hong");
        worklist.setResName("Hong User");
        worklist.setGroupCd("ORG-OLD");
        return worklist;
    }
}
