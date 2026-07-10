package org.uengine.hwlife.rule;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;

@RestController
@RequestMapping("/role-assign-rules")
public class RoleAssignRuleController {

    private final RuleRoleResolutionService ruleService;
    private final RoleDistributionService distributionService;

    public RoleAssignRuleController(RuleRoleResolutionService ruleService,
                                    RoleDistributionService distributionService) {
        this.ruleService = ruleService;
        this.distributionService = distributionService;
    }

    @GetMapping
    public List<RoleAssignRuleResponse> list(
            @RequestParam String policyId,
            @RequestParam(required = false) String difficulty) {
        return ruleService.listRulesForDisplay(policyId, difficulty)
                .stream()
                .map(RoleAssignRuleResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/distribution")
    public RoleDistributionService.RoleDistributionSummary distribution(
            @RequestParam String policyId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String processDefinitionId) {
        return distributionService.summarize(policyId, difficulty, processDefinitionId);
    }

    public static class RoleAssignRuleResponse {
        private Long ruleId;
        private String policyId;
        private String difficulty;
        private String endpoint;
        private Double weight;
        private String useYn;
        private Date syncedAt;

        public static RoleAssignRuleResponse from(BpmRoleAssignRule rule) {
            RoleAssignRuleResponse response = new RoleAssignRuleResponse();
            response.ruleId = rule.getRuleId();
            response.policyId = rule.getPolicyId();
            response.difficulty = rule.getDifficulty();
            response.endpoint = rule.getEndpoint();
            response.weight = rule.getWeight();
            response.useYn = rule.getUseYn();
            response.syncedAt = rule.getSyncedAt();
            return response;
        }

        public Long getRuleId() {
            return ruleId;
        }

        public String getPolicyId() {
            return policyId;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public Double getWeight() {
            return weight;
        }

        public String getUseYn() {
            return useYn;
        }

        public Date getSyncedAt() {
            return syncedAt;
        }
    }
}
