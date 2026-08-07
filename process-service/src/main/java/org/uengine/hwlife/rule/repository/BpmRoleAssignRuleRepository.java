package org.uengine.hwlife.rule.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.uengine.hwlife.rule.entity.BpmRoleAssignRule;

/**
 * BPM_ROLE_ASSIGN_RULE 접근 리포지토리.
 */
public interface BpmRoleAssignRuleRepository extends JpaRepository<BpmRoleAssignRule, Long> {

    List<BpmRoleAssignRule> findByPolicyIdAndDifficultyAndUseYn(String policyId, String difficulty, String useYn);

    List<BpmRoleAssignRule> findByPolicyIdAndUseYn(String policyId, String useYn);

    List<BpmRoleAssignRule> findByPolicyId(String policyId);

    /** 정책 단위 동기화 여부 판단용 — use_yn 무관하게 최근 동기화 시각. */
    @Query("select max(r.syncedAt) from BpmRoleAssignRule r where r.policyId = :policyId")
    Date findMaxSyncedAtByPolicyId(@Param("policyId") String policyId);
}
