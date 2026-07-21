package org.uengine.hwlife.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.uengine.hwlife.rule.RuleCandidate;
import org.uengine.kernel.ProcessInstance;

class RuleBasedRoleResolutionContextTest {

    private final RuleBasedRoleResolutionContext context = new RuleBasedRoleResolutionContext();

    @Test
    void selectByGap_prefersLowerLoadWhenUsersAreEquallyBelowTarget() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("user_a", "HIGH", 3),
                new RuleCandidate("user_b", "HIGH", 5),
                new RuleCandidate("user_c", "HIGH", 2));

        String selected = context.selectByGap(rules, Map.of(
                "user_a", 8,
                "user_b", 9,
                "user_c", 3));

        assertEquals("user_c", selected);
    }

    @Test
    void selectByGap_skipsUserAboveTargetRatio() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("user_a", "HIGH", 3),
                new RuleCandidate("user_b", "HIGH", 5),
                new RuleCandidate("user_c", "HIGH", 2));

        String selected = context.selectByGap(rules, Map.of(
                "user_a", 8,
                "user_b", 8,
                "user_c", 4));

        assertEquals("user_b", selected);
    }

    @Test
    void selectByGap_prefersLowerLoadWhenGapIsSame() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("user_a", "HIGH", 1),
                new RuleCandidate("user_b", "HIGH", 1));

        String selected = context.selectByGap(rules, Map.of(
                "user_a", 1,
                "user_b", 0));

        assertEquals("user_b", selected);
    }

    @Test
    void selectByGap_prefersHigherWeightWhenGapAndLoadAreSame() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("user_a", "HIGH", 3),
                new RuleCandidate("user_b", "HIGH", 5));

        String selected = context.selectByGap(rules, Map.of(
                "user_a", 0,
                "user_b", 0));

        assertEquals("user_b", selected);
    }

    @Test
    void selectByGap_usesMinimumWeightOfOne() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("user_a", "HIGH", 0),
                new RuleCandidate("user_b", "HIGH", 2));

        String selected = context.selectByGap(rules, Map.of(
                "user_a", 0,
                "user_b", 1));

        assertEquals("user_a", selected);
    }

    @Test
    void selectByGap_throwsWhenNoAssignableEndpointExists() {
        List<RuleCandidate> rules = List.of(
                new RuleCandidate("", "HIGH", 3),
                new RuleCandidate(null, "HIGH", 5));

        assertThrows(IllegalStateException.class, () -> context.selectByGap(rules, Map.of()));
    }

    @Test
    void resolveDirectAssignee_readsConfiguredPayloadKey() throws Exception {
        RuleBasedRoleResolutionContext.DynamicAssignment dynamicAssignment =
                new RuleBasedRoleResolutionContext.DynamicAssignment();
        dynamicAssignment.setEnabled(true);
        dynamicAssignment.setPayloadKey("reviewerEndpoint");
        context.setDynamicAssignment(dynamicAssignment);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "reviewerEndpoint")).thenReturn(" hong ");

        assertEquals("hong", context.resolveDirectAssignee(instance, "start"));
    }

    @Test
    void resolveDirectAssignee_ignoresMissingOrNonStringPayloadValue() throws Exception {
        RuleBasedRoleResolutionContext.DynamicAssignment dynamicAssignment =
                new RuleBasedRoleResolutionContext.DynamicAssignment();
        dynamicAssignment.setEnabled(true);
        dynamicAssignment.setPayloadKey("reviewerEndpoint");
        context.setDynamicAssignment(dynamicAssignment);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.get("", "reviewerEndpoint")).thenReturn(100);

        assertNull(context.resolveDirectAssignee(instance, "start"));
    }
}
