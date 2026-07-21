package org.uengine.hwlife.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.uengine.five.repository.RoleMappingRepository;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;
import org.uengine.hwlife.rule.repository.BpmRoleAssignRuleRepository;

import static org.mockito.Mockito.mock;

class RuleRoleResolutionServiceTest {

    @Test
    void loadRules_usesFreshLocalRulesWithoutExternalLookup() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        RuleRoleResolutionService service = service(ruleRepository, externalClient);

        when(ruleRepository.findByPolicyIdAndDifficultyAndUseYn("PLN01", "HIGH", "Y"))
                .thenReturn(List.of(rule("user_a", startOfToday())));

        List<RuleCandidate> result = service.loadRules("PLN01", "HIGH");

        assertEquals("user_a", result.get(0).getEndpoint());
        verify(externalClient, never()).fetchRules("PLN01", "HIGH");
    }

    @Test
    void loadRules_refreshesRulesSyncedBeforeTodayBeforeReturningCandidates() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        RuleRoleResolutionService service = service(ruleRepository, externalClient);
        BpmRoleAssignRule staleRule = rule("user_a", Date.from(startOfToday().toInstant().minusMillis(1)));
        BpmRoleAssignRule refreshedRule = rule("user_b", new Date());
        ExternalRoleAssignRule externalRule = new ExternalRoleAssignRule();
        externalRule.setEndpoint("user_b");
        externalRule.setDifficulty("HIGH");
        externalRule.setWeight(3d);

        when(ruleRepository.findByPolicyIdAndDifficultyAndUseYn("PLN01", "HIGH", "Y"))
                .thenReturn(List.of(staleRule), List.of(refreshedRule));
        when(externalClient.fetchRules("PLN01", "HIGH")).thenReturn(List.of(externalRule));
        when(ruleRepository.findFirstByPolicyIdAndDifficultyAndEndpoint("PLN01", "HIGH", "user_b"))
                .thenReturn(Optional.of(refreshedRule));

        List<RuleCandidate> result = service.loadRules("PLN01", "HIGH");

        assertEquals("user_b", result.get(0).getEndpoint());
        verify(externalClient).fetchRules("PLN01", "HIGH");
        verify(ruleRepository).saveAll(List.of(refreshedRule));
        verify(ruleRepository).flush();
    }

    @Test
    void loadRules_fetchesExternalRulesWhenNoLocalRuleExists() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        RuleRoleResolutionService service = service(ruleRepository, externalClient);
        BpmRoleAssignRule refreshedRule = rule("user_a", new Date());

        when(ruleRepository.findByPolicyIdAndDifficultyAndUseYn("PLN01", "HIGH", "Y"))
                .thenReturn(List.of(), List.of(refreshedRule));
        when(externalClient.fetchRules("PLN01", "HIGH")).thenReturn(List.of(externalRule("user_a", "HIGH", 3d)));
        when(ruleRepository.findFirstByPolicyIdAndDifficultyAndEndpoint("PLN01", "HIGH", "user_a"))
                .thenReturn(Optional.of(refreshedRule));

        List<RuleCandidate> result = service.loadRules("PLN01", "HIGH");

        assertEquals("user_a", result.get(0).getEndpoint());
        verify(externalClient).fetchRules("PLN01", "HIGH");
    }

    @Test
    void refreshRulesDaily_refreshesEachKnownPolicyAndDifficultyOnce() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        RuleRoleResolutionService service = service(ruleRepository, externalClient);
        BpmRoleAssignRule first = rule("user_a", new Date());
        first.setPolicyId("PLN01");
        first.setDifficulty("HIGH");
        BpmRoleAssignRule duplicate = rule("user_b", new Date());
        duplicate.setPolicyId("PLN01");
        duplicate.setDifficulty("HIGH");
        when(ruleRepository.findAll()).thenReturn(List.of(first, duplicate));
        when(externalClient.fetchRules("PLN01", "HIGH")).thenReturn(List.of());

        service.refreshRulesDaily();

        verify(externalClient).fetchRules("PLN01", "HIGH");
    }

    @Test
    void refreshRulesDaily_continuesAfterOnePolicyRefreshFails() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        RuleRoleResolutionService service = service(ruleRepository, externalClient);
        BpmRoleAssignRule failingRule = rule("user_a", new Date());
        BpmRoleAssignRule succeedingRule = rule("user_b", new Date());
        succeedingRule.setPolicyId("PLN02");
        succeedingRule.setDifficulty("LOW");
        when(ruleRepository.findAll()).thenReturn(List.of(failingRule, succeedingRule));
        when(externalClient.fetchRules("PLN01", "HIGH")).thenThrow(new IllegalStateException("source unavailable"));
        when(externalClient.fetchRules("PLN02", "LOW")).thenReturn(List.of());

        service.refreshRulesDaily();

        verify(externalClient).fetchRules("PLN01", "HIGH");
        verify(externalClient).fetchRules("PLN02", "LOW");
    }

    @Test
    void refreshRulesDaily_usesTheConfigurableMidnightDefaultSchedule() throws NoSuchMethodException {
        Scheduled scheduled = RuleRoleResolutionService.class
                .getMethod("refreshRulesDaily")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${uengine.role-assign-rule.refresh-cron:0 0 0 * * *}", scheduled.cron());
    }

    @Test
    void resolveActiveDelegateEndpoint_returnsCurrentDelegate() {
        BpmRoleAssignRuleRepository ruleRepository = mock(BpmRoleAssignRuleRepository.class);
        ExternalRoleAssignRuleClient externalClient = mock(ExternalRoleAssignRuleClient.class);
        AbsenceRepository absenceRepository = mock(AbsenceRepository.class);
        RuleRoleResolutionService service = new RuleRoleResolutionService(
                ruleRepository,
                externalClient,
                mock(RoleMappingRepository.class),
                absenceRepository);
        AbsenceEntity absence = new AbsenceEntity();
        absence.setAgentUserId("kim");
        when(absenceRepository.findActiveAt(org.mockito.ArgumentMatchers.eq("hong"), org.mockito.ArgumentMatchers.any(Date.class)))
                .thenReturn(List.of(absence));

        assertEquals("kim", service.resolveActiveDelegateEndpoint(" hong "));
    }

    private static RuleRoleResolutionService service(BpmRoleAssignRuleRepository ruleRepository,
                                                      ExternalRoleAssignRuleClient externalClient) {
        return new RuleRoleResolutionService(ruleRepository, externalClient, mock(RoleMappingRepository.class));
    }

    private static BpmRoleAssignRule rule(String endpoint, Date syncedAt) {
        BpmRoleAssignRule rule = new BpmRoleAssignRule();
        rule.setPolicyId("PLN01");
        rule.setDifficulty("HIGH");
        rule.setEndpoint(endpoint);
        rule.setWeight(1d);
        rule.setUseYn("Y");
        rule.setSyncedAt(syncedAt);
        return rule;
    }

    private static Date startOfToday() {
        return Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static ExternalRoleAssignRule externalRule(String endpoint, String difficulty, double weight) {
        ExternalRoleAssignRule rule = new ExternalRoleAssignRule();
        rule.setEndpoint(endpoint);
        rule.setDifficulty(difficulty);
        rule.setWeight(weight);
        return rule;
    }
}
