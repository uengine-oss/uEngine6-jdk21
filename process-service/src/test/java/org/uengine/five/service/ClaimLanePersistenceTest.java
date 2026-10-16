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
        UserContext.getThreadLocalInstance().setUserId("hong");
        UserContext.getThreadLocalInstance().setGroups(java.util.List.of("00025"));
        UserContext.getThreadLocalInstance().setScopes(java.util.List.of("scope"));
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

    @AfterEach void clearUser() { UserContext.getThreadLocalInstance().setUserId(null); UserContext.getThreadLocalInstance().setGroups(null); UserContext.getThreadLocalInstance().setScopes(null); }

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
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.claimWorkItem("100", command)).getStatusCode().value());
        assertEquals("kim", lane.getEndpoint());
        verifyNoInteractions(instance);
    }

    @Test void agentClaimPersistsOrganizationWithoutChangingOrdinaryClaim() throws Exception {
        RoleMappingCommand command = new RoleMappingCommand();
        command.setEndpoint("hong");
        command.setGroupName("AGENT_ORG");
        WorklistEntity sibling = new WorklistEntity();
        sibling.setGroupCd("00025");
        when(service.worklistRepository.findSiblingsForClaimState(10L, "team", "scope", "00025", 3, null))
                .thenReturn(java.util.List.of(sibling));
        work.setResName("Absent employee");
        var identity = mock(RoleMapping.class);
        when(identity.getResourceName()).thenReturn("Hong");
        try (var factory = mockStatic(RoleMapping.class)) {
            factory.when(RoleMapping::create).thenReturn(identity);
            service.claimWorkItem("100", command, true);
        }
        assertEquals("Hong", work.getResName());
        assertEquals("Hong", lane.getResourceName());
        assertEquals("AGENT_ORG", work.getGroupCd());
        assertEquals("AGENT_ORG", sibling.getGroupCd());
        assertEquals("hong", sibling.getEndpoint());
        assertEquals("AGENT_ORG", lane.getGroupName());
        assertEquals("scope", lane.getScope());
        assertEquals(3, lane.getAssignType());
    }

    @Test void ordinaryClaimIgnoresRequestedOrganization() throws Exception {
        RoleMappingCommand command = new RoleMappingCommand();
        command.setEndpoint("hong"); command.setGroupName("OTHER");
        service.claimWorkItem("100", command);
        assertEquals("00025", work.getGroupCd());
        assertEquals("00025", lane.getGroupName());
    }

    @Test void commonLookupReturnsAgentAndNullWithoutOverwritingHistory() {
        var repository = mock(org.uengine.hwlife.absence.repository.AbsenceRepository.class);
        var absences = new org.uengine.hwlife.absence.AbsenceServiceImpl(repository);
        var active = new org.uengine.hwlife.absence.entity.AbsenceEntity();
        active.setAgentUserId("kim"); active.setAgentGroupCd("AGENT_ORG");
        when(repository.findActiveByUserIdAt(eq("hong"), any())).thenReturn(java.util.List.of(active));
        var agent = absences.findActiveAgent(" hong ");
        assertEquals("kim", agent.getAgntEmnb());
        assertEquals("AGENT_ORG", agent.getAgntFncgOrgnCode());
        when(repository.findActiveByUserIdAt(eq("hong"), any())).thenReturn(java.util.List.of());
        assertNull(absences.findActiveAgent("hong"));
        assertNull(absences.findActiveAgent(" "));
        verify(repository, never()).save(any());
    }

    @Test void directTaskCreationAppliesAgentAfterBoundAndSkipsOtherContexts() throws Exception {
        var worklist = new org.uengine.five.overriding.JPAWorkList();
        var absences = mock(org.uengine.hwlife.absence.AbsenceServiceImpl.class);
        var field = worklist.getClass().getDeclaredField("absenceService"); field.setAccessible(true); field.set(worklist, absences);
        var method = worklist.getClass().getDeclaredMethod("applyDirectAssignmentAgent", RoleMapping.class,
                java.util.Map.class, boolean.class, org.uengine.processmanager.TransactionContext.class);
        method.setAccessible(true);
        var tc = mock(org.uengine.processmanager.ProcessTransactionContext.class);
        var pd = mock(org.uengine.kernel.ProcessDefinition.class);
        var role = new org.uengine.kernel.Role(); role.setName("team");
        var bound = new org.uengine.hwlife.overriding.BoundRoleResolutionContext();
        bound.setBase(new org.uengine.kernel.DirectRoleResolutionContext()); role.setRoleResolutionContext(bound);
        when(tc.getProcessInstanceInTransaction("10")).thenReturn(instance);
        when(instance.getProcessDefinition()).thenReturn(pd); when(pd.getRole("team")).thenReturn(role);
        when(pd.getActivity("Task")).thenReturn(new org.uengine.kernel.HumanActivity());
        doAnswer(invocation -> { lane.setEndpoint("kim"); lane.setGroupName("AGENT_ORG"); lane.setResourceName("Kim"); return true; })
                .when(absences).applyActiveAgent(lane);
        var parameters = new java.util.HashMap<String,Object>(); parameters.put("instanceId", "10"); parameters.put("roleName", "team"); parameters.put("tracingTag", "Task");
        lane.setEndpoint("hong");
        method.invoke(worklist, lane, parameters, false, tc);
        assertEquals("kim", lane.getEndpoint()); assertEquals("AGENT_ORG", parameters.get("groupName"));
        verify(instance).putRoleMapping("team", lane);
        reset(absences); clearInvocations(instance);
        lane.setEndpoint("hong"); lane.setGroupName("00025");
        parameters.put("groupName", "00025");
        method.invoke(worklist, lane, parameters, false, tc);
        assertEquals("hong", lane.getEndpoint()); assertEquals("00025", parameters.get("groupName"));
        verify(instance, never()).putRoleMapping(any(String.class), any(RoleMapping.class));
        clearInvocations(absences);
        role.setRoleResolutionContext(new org.uengine.five.overriding.IAMRoleResolutionContext());
        method.invoke(worklist, lane, parameters, false, tc);
        verifyNoInteractions(absences);
        role.setRoleResolutionContext(bound);
        method.invoke(worklist, lane, parameters, true, tc);
        verifyNoInteractions(absences);
    }

    @Test void commonAgentReplacementKeepsDispatchMetadataAndUsesOneLookup() throws Exception {
        var repository = mock(org.uengine.hwlife.absence.repository.AbsenceRepository.class);
        var absences = new org.uengine.hwlife.absence.AbsenceServiceImpl(repository);
        var active = new org.uengine.hwlife.absence.entity.AbsenceEntity();
        active.setAgentUserId("kim"); active.setAgentGroupCd("AGENT_ORG");
        when(repository.findActiveByUserIdAt(eq("hong"), any())).thenReturn(java.util.List.of(active));
        lane.setEndpoint("hong"); lane.setResourceName("Hong"); lane.setDispatchingOption(1);
        var identity = mock(RoleMapping.class);
        when(identity.getResourceName()).thenReturn("Kim");
        try (var factory = mockStatic(RoleMapping.class)) {
            factory.when(RoleMapping::create).thenReturn(identity);
            assertTrue(absences.applyActiveAgent(lane));
        }
        assertEquals("kim", lane.getEndpoint()); assertEquals("Kim", lane.getResourceName());
        assertEquals("AGENT_ORG", lane.getGroupName()); assertEquals("scope", lane.getScope());
        assertEquals(3, lane.getAssignType()); assertEquals(1, lane.getDispatchingOption());
        verify(repository, times(1)).findActiveByUserIdAt(eq("hong"), any());
        verify(repository, never()).findActiveByUserIdAt(eq("kim"), any());
        verify(identity).fill();
        assertFalse(absences.applyActiveAgent(lane));
        assertEquals("kim", lane.getEndpoint());
    }

    @Test void ruleSelectionAppliesAgentBeforePersistingWithoutCallingExternalServices() throws Exception {
        var ruleService = mock(org.uengine.hwlife.rule.RuleRoleResolutionService.class);
        org.uengine.hwlife.rule.RuleRoleResolutionSupport.register(ruleService);
        try {
            when(ruleService.syncRoleAssignRules("POLICY", "LOW")).thenReturn(java.util.List.of(
                new org.uengine.hwlife.rule.dto.RuleCandidate("hong", "LOW", 2),
                new org.uengine.hwlife.rule.dto.RuleCandidate("kim", "LOW", 1)));
            when(ruleService.findExcludedEndpointsByRefId("REF")).thenReturn(java.util.Set.of());
            when(ruleService.queryWorkload(eq("REF"), any())).thenReturn(java.util.Map.of("hong", 0, "kim", 0));
            when(instance.getLocalInstance()).thenReturn(instance);
            when(instance.get("", "corrKey")).thenReturn("REF");
            var activeAgent = new java.util.concurrent.atomic.AtomicBoolean(true);
            doAnswer(invocation -> {
                RoleMapping selected = invocation.getArgument(0);
                assertEquals("hong", selected.getEndpoint());
                if (activeAgent.get()) {
                    selected.setEndpoint("agent"); selected.setGroupName("AGENT_ORG"); selected.setResourceName("Agent");
                } else { selected.setResourceName("Hong"); }
                return null;
            }).when(ruleService).applyAbsentAssignee(any());
            var context = new org.uengine.hwlife.overriding.RuleBasedRoleResolutionContext();
            context.setPolicyId("POLICY");
            var result = context.getActualMapping(null, instance, "Task", java.util.Map.of());
            assertEquals("agent", result.getEndpoint()); assertEquals("AGENT_ORG", result.getGroupName());
            verify(ruleService).persistRoleMapping(instance, result, "POLICY", "LOW", "REF");
            activeAgent.set(false);
            var unchanged = context.getActualMapping(null, instance, "Task", java.util.Map.of());
            assertEquals("hong", unchanged.getEndpoint()); assertNull(unchanged.getGroupName());
            verify(ruleService, never()).resolveAbsentAssignee(any());
        } finally { org.uengine.hwlife.rule.RuleRoleResolutionSupport.register(null); }
    }

    @Test void absenceRepositoryHonorsInclusivePeriodAndReleaseOnLocalDatabase() throws Exception {
        try (var factory = new org.hibernate.cfg.Configuration()
                .addAnnotatedClass(org.uengine.hwlife.absence.entity.AbsenceEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:absence_agent;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory(); var session = factory.openSession()) {
            session.beginTransaction();
            var record = new org.uengine.hwlife.absence.entity.AbsenceEntity();
            record.setUserId("hong"); record.setAgentUserId("kim"); record.setAgentGroupCd("AGENT_ORG");
            var zone = java.time.ZoneId.of("Asia/Seoul");
            var start = java.time.LocalDateTime.of(2026,10,7,0,0).atZone(zone).toInstant();
            var end = java.time.LocalDateTime.of(2026,10,10,23,59,59).atZone(zone).toInstant();
            record.setAbscStarDttm(java.util.Date.from(start)); record.setAbscEndDttm(java.util.Date.from(end));
            session.persist(record); session.flush();
            var repo = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(session)
                    .getRepository(org.uengine.hwlife.absence.repository.AbsenceRepository.class);
            var absences = new org.uengine.hwlife.absence.AbsenceServiceImpl(repo);
            var lookup = absences.getClass().getDeclaredMethod("findActiveAgent", String.class, java.util.Date.class);
            lookup.setAccessible(true);
            assertNull(lookup.invoke(absences, "hong", java.util.Date.from(start.minusSeconds(1))));
            assertNotNull(lookup.invoke(absences, "hong", java.util.Date.from(start)));
            assertNotNull(lookup.invoke(absences, "hong", java.util.Date.from(end)));
            assertNull(lookup.invoke(absences, "hong", java.util.Date.from(end.plusSeconds(1))));
            assertNull(lookup.invoke(absences, "kim", java.util.Date.from(start)));
            record.setAbscRscsDttm(java.util.Date.from(start)); session.flush();
            assertNull(lookup.invoke(absences, "hong", java.util.Date.from(start)));
            session.getTransaction().rollback();
        }
    }

    @Test void directCreationStoresAgentIdentityInWorklistOnLocalDatabase() throws Exception {
        try (var factory = new org.hibernate.cfg.Configuration()
                .addAnnotatedClass(org.uengine.five.entity.ProcessInstanceEntity.class)
                .addAnnotatedClass(org.uengine.five.entity.RoleMappingEntity.class)
                .addAnnotatedClass(WorklistEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:absence_worklist;NON_KEYWORDS=VALUE")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory(); var session = factory.openSession()) {
            session.beginTransaction();
            var process = new org.uengine.five.entity.ProcessInstanceEntity();
            process.setStatus("Running"); session.persist(process);
            String instanceId = String.valueOf(process.getInstId());
            var worklist = new org.uengine.five.overriding.JPAWorkList();
            var repository = mock(WorklistRepository.class);
            var field = worklist.getClass().getDeclaredField("worklistRepository");
            field.setAccessible(true); field.set(worklist, repository);
            when(repository.save(any())).thenAnswer(invocation -> {
                WorklistEntity saved = invocation.getArgument(0); session.persist(saved); return saved;
            });
            var absences = mock(org.uengine.hwlife.absence.AbsenceServiceImpl.class);
            field = worklist.getClass().getDeclaredField("absenceService"); field.setAccessible(true); field.set(worklist, absences);
            doAnswer(invocation -> {
                RoleMapping selected = invocation.getArgument(0);
                selected.setEndpoint("kim"); selected.setGroupName("AGENT_ORG"); selected.setResourceName("Kim"); return true;
            }).when(absences).applyActiveAgent(any());
            var tc = mock(org.uengine.processmanager.ProcessTransactionContext.class);
            when(tc.getProcessInstanceInTransaction(instanceId)).thenReturn(instance);
            var pd = mock(org.uengine.kernel.ProcessDefinition.class);
            when(instance.getProcessDefinition()).thenReturn(pd);
            var role = new org.uengine.kernel.Role(); role.setName("team");
            var direct = new org.uengine.kernel.DirectRoleResolutionContext();
            direct.setEndpoint("hong"); role.setRoleResolutionContext(direct);
            when(pd.getRole("team")).thenReturn(role);
            when(pd.getActivity("Task")).thenReturn(new org.uengine.kernel.HumanActivity());
            lane.setEndpoint("hong"); lane.setResourceName("Hong");
            var parameters = new org.uengine.kernel.KeyedParameter[] {
                new org.uengine.kernel.KeyedParameter("instanceId", instanceId),
                new org.uengine.kernel.KeyedParameter("rootInstanceId", instanceId),
                new org.uengine.kernel.KeyedParameter("roleName", "team"),
                new org.uengine.kernel.KeyedParameter("tracingTag", "Task"),
                new org.uengine.kernel.KeyedParameter("assignType", "0"),
                new org.uengine.kernel.KeyedParameter("groupName", "00025"),
                new org.uengine.kernel.KeyedParameter("resourceName", "Hong")
            };
            String taskId = worklist.addWorkItem(lane, parameters, tc);
            verify(absences).applyActiveAgent(lane);
            session.flush(); session.clear();
            WorklistEntity saved = session.find(WorklistEntity.class, Long.valueOf(taskId));
            assertEquals("kim", saved.getEndpoint()); assertEquals("AGENT_ORG", saved.getGroupCd());
            assertEquals("Kim", saved.getResName()); assertEquals("NEW", saved.getStatus());
            verify(instance).putRoleMapping("team", lane);
            session.getTransaction().rollback();
        }
    }
}
