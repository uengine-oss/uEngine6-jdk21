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

    List<BpmRoleAssignRule> findByPolicyIdAndDifficultyAndUseYn(String policyId, String difficulty, Boolean useYn);

    List<BpmRoleAssignRule> findByPolicyIdAndUseYn(String policyId, Boolean useYn);

    List<BpmRoleAssignRule> findByPolicyId(String policyId);

    /** (policyId, difficulty) 조합의 최근 동기화 시각 — use_yn·endpoint 무관. */
    @Query("select max(r.syncedAt) from BpmRoleAssignRule r "
            + "where r.policyId = :policyId and r.difficulty = :difficulty")
    Date findMaxSyncedAtByPolicyIdAndDifficulty(
            @Param("policyId") String policyId, @Param("difficulty") String difficulty);

    /** policyId 단위 최근 동기화 시각 (난이도 미지정 조회용). */
    @Query("select max(r.syncedAt) from BpmRoleAssignRule r where r.policyId = :policyId")
    Date findMaxSyncedAtByPolicyId(@Param("policyId") String policyId);
}
