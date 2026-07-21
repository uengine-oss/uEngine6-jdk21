package org.uengine.hwlife.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.rule.RoleDistributionService.RoleDistributionSummary;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

class RoleDistributionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void summarize_calculatesTargetActualAndDisplayGap() {
        RuleRoleResolutionService ruleService = mock(RuleRoleResolutionService.class);
        EntityManager entityManager = mock(EntityManager.class);

        when(ruleService.listRulesForDisplay("PLN01", "HIGH")).thenReturn(List.of(
                rule("PLN01", "HIGH", "user_a", 3, "Y"),
                rule("PLN01", "HIGH", "user_b", 5, "Y"),
                rule("PLN01", "HIGH", "user_c", 2, "Y")));

        TypedQuery<Object[]> activeCountQuery = mock(TypedQuery.class);
        TypedQuery<Object[]> totalCountQuery = mock(TypedQuery.class);
        TypedQuery<Object[]> processRowsQuery = mock(TypedQuery.class);
        TypedQuery<WorklistEntity> recentRowsQuery = mock(TypedQuery.class);

        when(entityManager.createQuery(anyString(), eq(Object[].class)))
                .thenReturn(activeCountQuery)
                .thenReturn(totalCountQuery)
                .thenReturn(processRowsQuery);
        when(entityManager.createQuery(anyString(), eq(WorklistEntity.class))).thenReturn(recentRowsQuery);

        when(activeCountQuery.setParameter(eq("endpoints"), org.mockito.ArgumentMatchers.any())).thenReturn(activeCountQuery);
        when(activeCountQuery.setParameter("completed", "COMPLETED")).thenReturn(activeCountQuery);
        when(activeCountQuery.setParameter("policyId", "PLN01")).thenReturn(activeCountQuery);
        when(activeCountQuery.setParameter("difficulty", "HIGH")).thenReturn(activeCountQuery);
        when(activeCountQuery.getResultList()).thenReturn(List.of(
                new Object[] {"user_a", 8L},
                new Object[] {"user_b", 9L},
                new Object[] {"user_c", 3L}));

        when(totalCountQuery.setParameter(eq("endpoints"), org.mockito.ArgumentMatchers.any())).thenReturn(totalCountQuery);
        when(totalCountQuery.setParameter("policyId", "PLN01")).thenReturn(totalCountQuery);
        when(totalCountQuery.setParameter("difficulty", "HIGH")).thenReturn(totalCountQuery);
        when(totalCountQuery.getResultList()).thenReturn(List.of(
                new Object[] {"user_a", 8L},
                new Object[] {"user_b", 9L},
                new Object[] {"user_c", 3L}));

        when(processRowsQuery.setParameter(eq("endpoints"), org.mockito.ArgumentMatchers.any())).thenReturn(processRowsQuery);
        when(processRowsQuery.setParameter("completed", "COMPLETED")).thenReturn(processRowsQuery);
        when(processRowsQuery.setParameter("policyId", "PLN01")).thenReturn(processRowsQuery);
        when(processRowsQuery.setParameter("difficulty", "HIGH")).thenReturn(processRowsQuery);
        when(processRowsQuery.getResultList()).thenReturn(List.of());

        when(recentRowsQuery.setParameter(eq("endpoints"), org.mockito.ArgumentMatchers.any())).thenReturn(recentRowsQuery);
        when(recentRowsQuery.setParameter("policyId", "PLN01")).thenReturn(recentRowsQuery);
        when(recentRowsQuery.setParameter("difficulty", "HIGH")).thenReturn(recentRowsQuery);
        when(recentRowsQuery.setMaxResults(30)).thenReturn(recentRowsQuery);
        when(recentRowsQuery.getResultList()).thenReturn(List.of());

        RoleDistributionService service = new RoleDistributionService(ruleService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        RoleDistributionSummary summary = service.summarize("PLN01", "HIGH", null);

        assertEquals(3, summary.users.size());
        assertEquals(20, summary.totals.activeWorkItemCount);

        assertEquals("user_a", summary.users.get(0).endpoint);
        assertEquals(0.3, summary.users.get(0).targetRatio, 0.0001);
        assertEquals(0.4, summary.users.get(0).actualRatio, 0.0001);
        assertEquals(0.1, summary.users.get(0).gap, 0.0001);

        assertEquals("user_b", summary.users.get(1).endpoint);
        assertEquals(0.5, summary.users.get(1).targetRatio, 0.0001);
        assertEquals(0.45, summary.users.get(1).actualRatio, 0.0001);
        assertEquals(-0.05, summary.users.get(1).gap, 0.0001);

        assertEquals("user_c", summary.users.get(2).endpoint);
        assertEquals(0.2, summary.users.get(2).targetRatio, 0.0001);
        assertEquals(0.15, summary.users.get(2).actualRatio, 0.0001);
        assertEquals(-0.05, summary.users.get(2).gap, 0.0001);
    }

    @Test
    void summarize_returnsEmptySummaryWhenNoActiveRulesExist() {
        RuleRoleResolutionService ruleService = mock(RuleRoleResolutionService.class);
        when(ruleService.listRulesForDisplay("PLN01", "HIGH")).thenReturn(List.of(
                rule("PLN01", "HIGH", "user_a", 3, "N")));

        RoleDistributionService service = new RoleDistributionService(ruleService);

        RoleDistributionSummary summary = service.summarize("PLN01", "HIGH", null);

        assertEquals(0, summary.users.size());
        assertEquals(0, summary.processes.size());
        assertEquals(0, summary.recentWorkItems.size());
        assertEquals(0, summary.totals.activeWorkItemCount);
    }

    private static BpmRoleAssignRule rule(String policyId, String difficulty, String endpoint, double weight, String useYn) {
        BpmRoleAssignRule rule = new BpmRoleAssignRule();
        rule.setPolicyId(policyId);
        rule.setDifficulty(difficulty);
        rule.setEndpoint(endpoint);
        rule.setWeight(weight);
        rule.setUseYn(useYn);
        return rule;
    }
}
