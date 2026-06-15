package org.uengine.hwlife.rule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;

/**
 * BPM_ROLE_ASSIGN_RULE 접근 리포지토리.
 */
public interface BpmRoleAssignRuleRepository extends JpaRepository<BpmRoleAssignRule, Long> {

    List<BpmRoleAssignRule> findByPolicyIdAndDifficultyAndUseYn(String policyId, String difficulty, String useYn);

    List<BpmRoleAssignRule> findByPolicyIdAndUseYn(String policyId, String useYn);
}
