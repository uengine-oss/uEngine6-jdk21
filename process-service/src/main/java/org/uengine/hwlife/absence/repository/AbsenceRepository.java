package org.uengine.hwlife.absence.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.uengine.hwlife.absence.entity.AbsenceEntity;

/**
 * 부재자/대결자 설정 저장소.
 *
 * <p>JpaRepository 기본 메서드 (save / findById / deleteById) 외에,
 * 사번(userId) 으로 모든 이력을 조회하는 {@link #findByUserId(String)} 와,
 * 등록 시 기간 중복/상호 부재 검사를 위한 조회를 제공합니다.</p>
 */
public interface AbsenceRepository extends JpaRepository<AbsenceEntity, Long> {

    /** 특정 사용자(사번)의 모든 부재 이력 (최근순) */
    @Query("select a from AbsenceEntity a where a.userId = :userId order by a.abscStupDttm desc")
    List<AbsenceEntity> findByUserId(@Param("userId") String userId);

    @Query("select a from AbsenceEntity a where a.userId = :userId order by a.abseId desc")
    List<AbsenceEntity> findHistoryFirstPage(@Param("userId") String userId, Pageable pageable);

    @Query("select a from AbsenceEntity a where a.userId = :userId and a.abseId <= :nextKey order by a.abseId desc")
    List<AbsenceEntity> findHistoryPageAfter(@Param("userId") String userId,
                                             @Param("nextKey") Long nextKey,
                                             Pageable pageable);

    long countByUserId(String userId);

    /**
     * 동일 userId 로 기간이 겹치는 활성 부재가 존재하는지 검사 (등록 시 중복 방지용).
     *
     * @param userId       부재자
     * @param newAbscStarDttm 새로 등록할 시작일시
     * @param newAbscEndDttm  새로 등록할 종료일시 (null 이면 무한대로 간주 → 어떤 활성이든 충돌)
     * @param excludeAbseId   제외할 abseId (등록 시 -1L 전달)
     */
    @Query("select a from AbsenceEntity a " +
            "where a.userId = :userId " +
            "  and a.abscRscsDttm is null " +
            "  and a.abseId <> :excludeAbseId " +
            "  and a.abscStarDttm <= :newAbscEndDttm " +
            "  and ( a.abscEndDttm is null or a.abscEndDttm >= :newAbscStarDttm )")
    List<AbsenceEntity> findOverlappingActiveWithEnd(@Param("userId") String userId,
                                                      @Param("newAbscStarDttm") Date newAbscStarDttm,
                                                      @Param("newAbscEndDttm") Date newAbscEndDttm,
                                                      @Param("excludeAbseId") Long excludeAbseId);

    @Query("select a from AbsenceEntity a " +
            "where a.userId = :userId " +
            "  and a.abscRscsDttm is null " +
            "  and a.abseId <> :excludeAbseId " +
            "  and ( a.abscEndDttm is null or a.abscEndDttm >= :newAbscStarDttm )")
    List<AbsenceEntity> findOverlappingActiveWithoutEnd(@Param("userId") String userId,
                                                         @Param("newAbscStarDttm") Date newAbscStarDttm,
                                                         @Param("excludeAbseId") Long excludeAbseId);

    /**
     * 상대(agentUserId)가 요청자(userId)를 대결자로 둔 활성 부재가 기간 겹치는지 검사.
     * {@code newAbscEndDttm} 이 null 이면 신규 종료를 무한으로 본다.
     * PostgreSQL 은 {@code ? is null} 의 바인드 타입을 추론하지 못하므로 timestamp 로 캐스팅한다.
     */
    @Query("select a from AbsenceEntity a " +
            "where a.userId = :agentUserId " +
            "  and a.agentUserId = :userId " +
            "  and a.abscRscsDttm is null " +
            "  and ( cast(:newAbscEndDttm as timestamp) is null or a.abscStarDttm <= :newAbscEndDttm ) " +
            "  and ( a.abscEndDttm is null or a.abscEndDttm >= :newAbscStarDttm )")
    List<AbsenceEntity> findReciprocalActive(@Param("userId") String userId,
                                             @Param("agentUserId") String agentUserId,
                                             @Param("newAbscStarDttm") Date newAbscStarDttm,
                                             @Param("newAbscEndDttm") Date newAbscEndDttm);

    /** 현재 시각 기준 활성 부재(해제되지 않고 기간 내) 조회 — 대결자 라우팅용. */
    @Query("select a from AbsenceEntity a "
            + "where a.userId = :userId "
            + "  and a.abscRscsDttm is null "
            + "  and a.abscStarDttm <= :now "
            + "  and (a.abscEndDttm is null or a.abscEndDttm >= :now) "
            + "order by a.abscStarDttm desc")
    List<AbsenceEntity> findActiveByUserIdAt(@Param("userId") String userId, @Param("now") Date now);
}
