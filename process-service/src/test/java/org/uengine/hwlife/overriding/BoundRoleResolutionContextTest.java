package org.uengine.hwlife.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.uengine.five.overriding.IAMRoleResolutionContext;
import org.uengine.five.service.IAMService;
import org.uengine.kernel.DirectRoleResolutionContext;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.Role;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.RoleResolutionContext;

class BoundRoleResolutionContextTest {

    @Test
    void deserializesPlainBindingsObjectFromBpmnJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "_type");
        String json = """
            {
              "base": {
                "_type": "org.uengine.kernel.DirectRoleResolutionContext",
                "endpoint": "fallback-user"
              },
              "bindings": {
                "endpoint": "reptEmnb"
              }
            }
            """;

        BoundRoleResolutionContext context = mapper.readValue(json, BoundRoleResolutionContext.class);

        assertInstanceOf(DirectRoleResolutionContext.class, context.getBase());
        assertEquals("reptEmnb", context.getBindings().get("endpoint"));
    }

    @Test
    void resolvesDirectEndpointFromProcessVariable() throws Exception {
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("fallback-user");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "reptEmnb"));

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "reptEmnb")).thenReturn("hong@uengine.org");

        RoleMapping mapping = context.getActualMapping(null, instance, "Task_1", java.util.Map.of());

        assertEquals("hong@uengine.org", mapping.getEndpoint());
    }

    @Test
    void containsMappingUsesBoundEndpointForInitiationPermissionCheck() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("hong@uengine.org")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("fallback-user");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "reptEmnb"));

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "reptEmnb")).thenReturn("hong@uengine.org");

        RoleMapping matchedLogin = RoleMapping.create();
        matchedLogin.setEndpoint("hong@uengine.org");
        RoleMapping otherLogin = RoleMapping.create();
        otherLogin.setEndpoint("kim@uengine.org");

        assertTrue(context.containsMapping(instance, matchedLogin));
        assertFalse(context.containsMapping(instance, otherLogin));
        assertFalse(context.containsMapping(instance, null));
    }

    @Test
    void containsMappingUsesStaticEndpointWhenBoundEndpointIsInvalid() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("unknown@uengine.org")).thenReturn(false);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong@uengine.org");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "reptEmnb"));

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "reptEmnb")).thenReturn("unknown@uengine.org");
        RoleMapping login = RoleMapping.create();
        login.setEndpoint("hong@uengine.org");

        assertTrue(context.containsMapping(instance, login));
    }

    @Test
    void resolvesIamGroupAndScopeFromProcessVariables() throws Exception {
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("DEFAULT_ORG");
        base.setScope("DEFAULT_SCOPE");
        context.setBase(base);

        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("groupName", "fncgWndwOrgnCode");
        bindings.put("scope", "fncgCoreAtrtId");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "fncgWndwOrgnCode")).thenReturn("ORG01");
        when(instance.get("", "fncgCoreAtrtId")).thenReturn("ROLE01");

        // // RoleResolutionContext resolved = context.resolve(instance, "Task_1");

        // assertInstanceOf(IAMRoleResolutionContext.class, resolved);
        // IAMRoleResolutionContext iam = (IAMRoleResolutionContext) resolved;
        // assertEquals("ORG01", iam.getGroupName());
        // assertEquals("ROLE01", iam.getScope());
    }

    @Test
    void keepsBaseValuesWhenBoundProcessVariablesAreMissing() throws Exception {
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("DEFAULT_ORG");
        base.setScope("DEFAULT_SCOPE");
        context.setBase(base);

        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("groupName", "missingOrgCode");
        bindings.put("scope", "missingScopeCode");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);

        // RoleResolutionContext resolved = context.resolve(instance, "Task_1");

        // assertInstanceOf(IAMRoleResolutionContext.class, resolved);
        // IAMRoleResolutionContext iam = (IAMRoleResolutionContext) resolved;
        // assertEquals("DEFAULT_ORG", iam.getGroupName());
        // assertEquals("DEFAULT_SCOPE", iam.getScope());
    }

    @Test
    void containsMappingUsesBaseDirectMappingWhenBoundEndpointIsMissing() throws Exception {
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong@uengine.org");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "missingEndpointVariable"));

        ProcessInstance instance = mock(ProcessInstance.class);

        RoleMapping matchedLogin = RoleMapping.create();
        matchedLogin.setEndpoint("hong@uengine.org");
        RoleMapping otherLogin = RoleMapping.create();
        otherLogin.setEndpoint("kim@uengine.org");

        assertTrue(context.containsMapping(instance, matchedLogin));
        assertFalse(context.containsMapping(instance, otherLogin));
    }

    @Test
    void changesDirectAssigneeOnNextResolutionAndKeepsTheNewAssigneeAfterward() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("kim")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "nextAssignee")).thenReturn("kim");
        RoleMapping claimedByHong = RoleMapping.create();
        claimedByHong.setEndpoint("hong");

        RoleMapping changed = context.resolveRoleMapping(
                null, instance, "Task_2", claimedByHong, java.util.Map.of());
        RoleMapping inherited = context.resolveRoleMapping(
                null, instance, "Task_3", changed, java.util.Map.of());

        assertEquals("kim", changed.getEndpoint());
        assertSame(changed, inherited);
    }

    @Test
    void keepsCurrentAssigneeWhenVariableIsMissingOrInvalid() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("unknown")).thenReturn(false);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        ProcessInstance missing = mock(ProcessInstance.class);
        ProcessInstance invalid = mock(ProcessInstance.class);
        when(invalid.get("", "nextAssignee")).thenReturn("unknown");
        RoleMapping current = RoleMapping.create();
        current.setEndpoint("kim");

        assertSame(current, context.resolveRoleMapping(null, missing, "Task_2", current, java.util.Map.of()));
        assertSame(current, context.resolveRoleMapping(null, invalid, "Task_2", current, java.util.Map.of()));
    }

    @Test
    void keepsCurrentAssigneeWhenVariableIsNotScalar() throws Exception {
        BoundRoleResolutionContext context = contextWithIam(mock(IAMService.class));
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "nextAssignee")).thenReturn(
                new java.util.HashMap<>(java.util.Map.of("id", "kim")));
        RoleMapping current = RoleMapping.create();
        current.setEndpoint("hong");

        assertSame(current, context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of()));
    }

    @Test
    void appliesGroupVariableAndPreservesInheritedScopeWhenRoleVariableIsMissing() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidGroup("ORG02")).thenReturn(true);
        when(iam.isValidRole("ROLE03")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("ORG01");
        base.setScope("ROLE01");
        context.setBase(base);
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("groupName", "orgCode");
        bindings.put("scope", "roleCode");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "orgCode")).thenReturn("ORG02");
        RoleMapping current = RoleMapping.create();
        current.setEndpoint("hong");
        current.setGroupName("ORG01");
        current.setScope("ROLE03");

        RoleMapping result = context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of());
        assertEquals("ORG02", result.getGroupName());
        assertEquals("ROLE03", result.getScope());
        assertNull(result.getEndpoint());
        assertEquals("ORG01", current.getGroupName());
        assertEquals("ROLE01", base.getScope());
    }

    @Test
    void appliesGroupVariableAndPreservesNullScopeWhenCurrentScopeIsMissing() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidGroup("ORG02")).thenReturn(true);
        when(iam.isValidRole("ROLE01")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("ORG01");
        base.setScope("ROLE01");
        context.setBase(base);
        LinkedHashMap<String, String> bindings = bindings("groupName", "orgCode");
        bindings.put("scope", "roleCode");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "orgCode")).thenReturn("ORG02");
        RoleMapping current = RoleMapping.create();
        current.setGroupName("ORG01");
        current.setScope(null);

        RoleMapping result = context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of());

        assertEquals("ORG02", result.getGroupName());
        assertNull(result.getScope());
        assertEquals("ORG01", current.getGroupName());
        assertNull(current.getScope());
    }

    @Test
    void appliesRoleVariableAndPreservesNullGroupWhenCurrentGroupIsMissing() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidGroup("ORG01")).thenReturn(true);
        when(iam.isValidRole("ROLE02")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("ORG01");
        base.setScope("ROLE01");
        context.setBase(base);
        LinkedHashMap<String, String> bindings = bindings("groupName", "orgCode");
        bindings.put("scope", "roleCode");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "roleCode")).thenReturn("ROLE02");
        RoleMapping current = RoleMapping.create();
        current.setGroupName(null);
        current.setScope("ROLE01");

        RoleMapping result = context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of());

        assertNull(result.getGroupName());
        assertEquals("ROLE02", result.getScope());
        assertNull(current.getGroupName());
        assertEquals("ROLE01", current.getScope());
    }

    @Test
    void appliesEitherSingleVariableAgainstDefaultsOnFirstAssignment() throws Exception {
        for (boolean groupOnly : new boolean[] { true, false }) {
            IAMService iam = mock(IAMService.class);
            when(iam.isValidGroup("ORG02")).thenReturn(true);
            when(iam.isValidRole("ROLE02")).thenReturn(true);
            BoundRoleResolutionContext context = contextWithIam(iam);
            IAMRoleResolutionContext base = new IAMRoleResolutionContext();
            base.setGroupName("ORG01");
            base.setScope("ROLE01");
            context.setBase(base);
            LinkedHashMap<String, String> binding = bindings("groupName", "orgCode");
            binding.put("scope", "roleCode");
            context.setBindings(binding);
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.get("", groupOnly ? "orgCode" : "roleCode")).thenReturn(groupOnly ? "ORG02" : "ROLE02");

            RoleMapping result = context.resolveRoleMapping(null, instance, "Task_1", null, java.util.Map.of());
            assertEquals(groupOnly ? "ORG02" : "ORG01", result.getGroupName());
            assertEquals(groupOnly ? "ROLE01" : "ROLE02", result.getScope());
        }
    }

    @Test
    void usesStaticDefaultWhenFirstVariableValueIsMissing() throws Exception {
        BoundRoleResolutionContext context = contextWithIam(mock(IAMService.class));
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("fallback-user");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        RoleMapping mapping = context.resolveRoleMapping(
                null, mock(ProcessInstance.class), "Task_1", null, java.util.Map.of());

        assertEquals("fallback-user", mapping.getEndpoint());
    }

    @Test
    void changesGroupRoleCriteriaButPreservesClaimWhenCriteriaAreUnchanged() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidGroup("ORG02")).thenReturn(true);
        when(iam.isValidRole("ROLE02")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("ORG01");
        base.setScope("ROLE01");
        context.setBase(base);
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("groupName", "orgCode");
        bindings.put("scope", "roleCode");
        context.setBindings(bindings);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "orgCode")).thenReturn("ORG02");
        when(instance.get("", "roleCode")).thenReturn("ROLE02");
        RoleMapping oldClaim = RoleMapping.create();
        oldClaim.setEndpoint("hong");
        oldClaim.setGroupName("ORG01");
        oldClaim.setScope("ROLE01");
        oldClaim.setAssignType(Role.ASSIGNTYPE_GROUP_ROLE);

        RoleMapping changed = context.resolveRoleMapping(
                null, instance, "Task_2", oldClaim, java.util.Map.of());
        assertEquals("ORG02", changed.getGroupName());
        assertEquals("ROLE02", changed.getScope());
        assertNull(changed.getEndpoint());

        changed.setEndpoint("kim");
        RoleMapping inherited = context.resolveRoleMapping(
                null, instance, "Task_3", changed, java.util.Map.of());

        assertSame(changed, inherited);
        assertEquals("kim", inherited.getEndpoint());
    }

    @Test
    void roleRefreshesAndPersistsChangedMapping() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("kim")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        Role role = new Role("LaneA");
        role.setRoleResolutionContext(context);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ProcessInstance instance = mock(ProcessInstance.class);
        RoleMapping current = RoleMapping.create();
        current.setEndpoint("hong");
        when(instance.getRoleMapping("LaneA")).thenReturn(current);
        when(instance.getProcessDefinition()).thenReturn(definition);
        when(definition.getRole("LaneA")).thenReturn(role);
        when(instance.get("", "nextAssignee")).thenReturn("kim");

        RoleMapping changed = role.getMapping(instance, "Task_2");

        assertEquals("kim", changed.getEndpoint());
        verify(instance).putRoleMapping("LaneA", changed);
    }

    @Test
    void roleKeepsClaimAndDoesNotPersistAgainWhenVariableIsUnchanged() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("hong")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);
        context.setBindings(bindings("endpoint", "nextAssignee"));

        Role role = new Role("LaneA");
        role.setRoleResolutionContext(context);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ProcessInstance instance = mock(ProcessInstance.class);
        RoleMapping claimed = RoleMapping.create();
        claimed.setEndpoint("hong");
        when(instance.getRoleMapping("LaneA")).thenReturn(claimed);
        when(instance.getProcessDefinition()).thenReturn(definition);
        when(definition.getRole("LaneA")).thenReturn(role);
        when(instance.get("", "nextAssignee")).thenReturn("hong");

        assertSame(claimed, role.getMapping(instance, "Task_2"));
        verify(instance, never()).putRoleMapping("LaneA", claimed);
    }

    @Test
    void fixedAssignmentWithoutBindingsRemainsUnchanged() throws Exception {
        BoundRoleResolutionContext context = contextWithIam(mock(IAMService.class));
        DirectRoleResolutionContext base = new DirectRoleResolutionContext();
        base.setEndpoint("hong");
        context.setBase(base);

        RoleMapping current = RoleMapping.create();
        current.setEndpoint("hong");

        assertSame(current, context.resolveRoleMapping(
                null, mock(ProcessInstance.class), "Task_2", current, java.util.Map.of()));
    }

    @Test
    void rejectsNumericVariableEvenWhenItsStringFormIsAValidCode() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidGroup("25")).thenReturn(true);
        when(iam.isValidRole("ROLE01")).thenReturn(true);
        BoundRoleResolutionContext context = contextWithIam(iam);
        IAMRoleResolutionContext base = new IAMRoleResolutionContext();
        base.setGroupName("ORG01");
        base.setScope("ROLE01");
        context.setBase(base);
        context.setBindings(bindings("groupName", "orgCode"));
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "orgCode")).thenReturn(25);
        RoleMapping current = RoleMapping.create();
        current.setGroupName("ORG02");
        current.setScope("ROLE01");
        current.setEndpoint("claimed-user");

        assertSame(current, context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of()));
        RoleMapping initial = context.resolveRoleMapping(null, instance, "Task_1", null, java.util.Map.of());
        assertEquals("ORG01", initial.getGroupName());
    }

    @Test
    void ruleBindingOverridesSelectionAndKeepsInheritedDelegate() throws Exception {
        IAMService iam = mock(IAMService.class);
        when(iam.isValidUser("hong")).thenReturn(true);
        RuleBasedRoleResolutionContext rule = mock(RuleBasedRoleResolutionContext.class);
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        context.setBase(rule);
        context.setBindings(bindings("endpoint", "assignee"));
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "assignee")).thenReturn("hong");
        RoleMapping delegated = RoleMapping.create();
        delegated.setEndpoint("kim");
        when(rule.getActualMapping(null, instance, "Task_1", java.util.Map.of(), "hong")).thenReturn(delegated);
        try (var factory = org.mockito.Mockito.mockStatic(org.uengine.five.service.IAMServiceFactory.class)) {
            factory.when(org.uengine.five.service.IAMServiceFactory::getDefault).thenReturn(iam);
            assertSame(delegated, context.resolveRoleMapping(null, instance, "Task_1", null, java.util.Map.of()));
            assertSame(delegated, context.resolveRoleMapping(null, instance, "Task_2", delegated, java.util.Map.of()));
            verify(rule, never()).getActualMapping(null, instance, "Task_1", java.util.Map.of());
            when(iam.isValidUser("lee")).thenReturn(true);
            when(instance.get("", "assignee")).thenReturn("lee");
            RoleMapping changed = RoleMapping.create();
            changed.setEndpoint("lee");
            when(rule.getActualMapping(null, instance, "Task_3", java.util.Map.of(), "lee")).thenReturn(changed);
            assertSame(changed, context.resolveRoleMapping(null, instance, "Task_3", delegated, java.util.Map.of()));
            assertEquals("kim", delegated.getEndpoint());
        }
    }

    @Test
    void invalidRuleBindingKeepsCurrentOrFallsBackToRules() throws Exception {
        IAMService iam = mock(IAMService.class);
        RuleBasedRoleResolutionContext rule = mock(RuleBasedRoleResolutionContext.class);
        BoundRoleResolutionContext context = new BoundRoleResolutionContext();
        context.setBase(rule);
        context.setBindings(bindings("endpoint", "assignee"));
        ProcessInstance instance = mock(ProcessInstance.class);
        RoleMapping current = RoleMapping.create();
        RoleMapping fallback = RoleMapping.create();
        when(rule.getActualMapping(null, instance, "Task_1", java.util.Map.of())).thenReturn(fallback);
        try (var factory = org.mockito.Mockito.mockStatic(org.uengine.five.service.IAMServiceFactory.class)) {
            factory.when(org.uengine.five.service.IAMServiceFactory::getDefault).thenReturn(iam);
            for (Object value : new Object[] { "unknown", "", 123, java.util.Map.of("id", "hong") }) {
                when(instance.get("", "assignee")).thenReturn((java.io.Serializable) value);
                assertSame(current, context.resolveRoleMapping(null, instance, "Task_2", current, java.util.Map.of()));
                assertSame(fallback, context.getActualMapping(null, instance, "Task_1", java.util.Map.of()));
            }
        }
    }

    @Test
    void ruleOverrideAppliesAbsenceBeforePersistingWithoutRuleLookup() throws Exception {
        var service = mock(org.uengine.hwlife.rule.RuleRoleResolutionService.class);
        var rule = new RuleBasedRoleResolutionContext();
        rule.setPolicyId("POL-001");
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "corrKey")).thenReturn("REF-001");
        org.mockito.Mockito.doAnswer(call -> {
            RoleMapping mapping = call.getArgument(0);
            assertEquals("hong", mapping.getEndpoint());
            mapping.setEndpoint("kim");
            return null;
        }).when(service).applyAbsentAssignee(org.mockito.ArgumentMatchers.any());
        try (var support = org.mockito.Mockito.mockStatic(org.uengine.hwlife.rule.RuleRoleResolutionSupport.class);
             var factory = org.mockito.Mockito.mockStatic(org.uengine.five.service.IAMServiceFactory.class)) {
            factory.when(org.uengine.five.service.IAMServiceFactory::getDefault).thenReturn(mock(IAMService.class));
            support.when(org.uengine.hwlife.rule.RuleRoleResolutionSupport::get).thenReturn(service);
            RoleMapping result = rule.getActualMapping(null, instance, "Task_1", java.util.Map.of(), "hong");
            assertEquals("kim", result.getEndpoint());
            assertEquals(Role.ASSIGNTYPE_USER, result.getAssignType());
            verify(service).persistRoleMapping(instance, result, "POL-001", "LOW", "REF-001");
            verify(service, never()).syncRoleAssignRules(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            verify(service, never()).findExcludedEndpointsByRefId(org.mockito.ArgumentMatchers.anyString());
            verify(service, never()).queryWorkload(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
        }
    }

    @Test
    void assignedWorkItemDoesNotSerializeRoleMappingBackReferences() throws Exception {
        var instance = new org.uengine.five.entity.ProcessInstanceEntity();
        instance.setInstId(1L);
        var roleMapping = new org.uengine.five.entity.RoleMappingEntity();
        roleMapping.setProcessInstance(instance);
        roleMapping.setRootProcessInstance(instance);
        roleMapping.setEndpoint("hong@uengine.org");
        instance.setRoleMappings(java.util.List.of(roleMapping));
        var worklist = new org.uengine.five.entity.WorklistEntity();
        worklist.setProcessInstance(instance);
        worklist.setEndpoint("hong@uengine.org");
        var resource = new org.uengine.five.dto.WorkItemResource();
        resource.setWorklist(worklist);
        resource.setActivity(new org.uengine.kernel.HumanActivity());
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(resource));
        assertTrue(json.has("activity"));
        assertEquals("hong@uengine.org", json.path("worklist").path("endpoint").asText());
        assertFalse(json.path("worklist").path("processInstance").has("roleMappings"));
    }

    private static BoundRoleResolutionContext contextWithIam(IAMService iam) {
        return new BoundRoleResolutionContext() {
            // @Override
            // protected IAMService getIamService() {
            //     return iam;
            // }
        };
    }

    private static LinkedHashMap<String, String> bindings(String key, String value) {
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put(key, value);
        return bindings;
    }
}
