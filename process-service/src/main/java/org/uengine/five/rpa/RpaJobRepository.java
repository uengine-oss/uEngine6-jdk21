package org.uengine.five.rpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RpaJobRepository extends JpaRepository<RpaJobEntity, String> {

    List<RpaJobEntity> findByInstanceIdOrderByCreatedDateAsc(String instanceId);

    List<RpaJobEntity> findByStatusAndModeOrderByCreatedDateAsc(String status, String mode);

    List<RpaJobEntity> findByStatusAndModeAndTargetUserOrderByCreatedDateAsc(String status, String mode,
            String targetUser);

    Optional<RpaJobEntity> findFirstByStatusAndModeOrderByCreatedDateAsc(String status, String mode);

    Optional<RpaJobEntity> findFirstByStatusAndModeAndTargetUserOrderByCreatedDateAsc(String status, String mode,
            String targetUser);

    /** 원자적 claim — QUEUED 인 경우에만 CLAIMED 로 전환 (동시 폴링 경합 방지). */
    @Modifying
    @Query("update RpaJobEntity j set j.status = 'CLAIMED', j.agentId = :agentId, j.claimedDate = CURRENT_TIMESTAMP"
            + " where j.jobId = :jobId and j.status = 'QUEUED'")
    int claim(@Param("jobId") String jobId, @Param("agentId") String agentId);

    List<RpaJobEntity> findByStatusIn(List<String> statuses);
}
