package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.RoleMappingCommand;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;

class ClaimLanePersistenceTest {
    private final ProcessInstance instance = mock(ProcessInstance.class);
    private final InstanceServiceImpl service = new InstanceServiceImpl() {
        @Override public ProcessInstance getProcessInstanceLocal(String id) { return instance; }
    };
    private final WorklistEntity work = new WorklistEntity();
    private final RoleMapping lane = mock(RoleMapping.class, CALLS_REAL_METHODS);

    ClaimLanePersistenceTest() throws Exception {
        service.worklistRepository = mock(WorklistRepository.class);
        service.assignmentStateService = mock(WorkItemAssignmentStateService.class);
        work.setTaskId(100L);
        work.setInstId(10L);
        work.setRootInstId(10L);
        work.setRoleName("team");
        work.setGroupCd("00025");
        work.setScope("scope");
        work.setAssignType(3);
        work.setDispatchOption(1);
        work.setResName("Hong");
        lane.setGroupName("00025");
        lane.setScope("scope");
        lane.setAssignType(3);
        when(service.worklistRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(work));
        when(instance.getRoleMapping("team")).thenReturn(lane);
    }

    @AfterEach void clearUser() { UserContext.getThreadLocalInstance().setUserId(null); }

    @Test void claimPersistsOwnerAndPreservesLaneMetadata() throws Exception {
        RoleMappingCommand command = new RoleMappingCommand();
        command.setEndpoint("hong");
        service.claimWorkItem("100", command);
        assertEquals("hong", lane.getEndpoint());
        assertEquals("00025", lane.getGroupName());
        assertEquals("scope", lane.getScope());
        assertEquals(3, lane.getAssignType());
        verify(instance).putRoleMapping("team", lane);
    }

    @Test void retryRepairsPreviouslyMissingLaneOwner() throws Exception {
        work.setEndpoint("hong");
        claimPersistsOwnerAndPreservesLaneMetadata();
    }

    @Test void unclaimClearsLaneOwnerButRetainsGroup() throws Exception {
        work.setEndpoint("hong");
        lane.setEndpoint("hong");
        UserContext.getThreadLocalInstance().setUserId("hong");
        service.claimWorkItem("100", new RoleMappingCommand());
        assertNull(lane.getEndpoint());
        assertNull(lane.getResourceName());
        assertEquals("00025", lane.getGroupName());
        verify(instance).putRoleMapping("team", lane);
    }

    @Test void conflictingClaimDoesNotChangeLane() {
        work.setEndpoint("kim");
        lane.setEndpoint("kim");
        RoleMappingCommand command = new RoleMappingCommand();
        command.setEndpoint("hong");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.claimWorkItem("100", command));
        assertEquals("kim", lane.getEndpoint());
        verifyNoInteractions(instance);
    }
}
