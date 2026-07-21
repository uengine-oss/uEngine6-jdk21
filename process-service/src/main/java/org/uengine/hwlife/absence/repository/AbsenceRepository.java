package org.uengine.hwlife.absence.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.uengine.hwlife.absence.entity.AbsenceEntity;

/**
 * 부재자/대결자 설정 저장소.
 *
 * <p>JpaRepository 기본 메서드 (save / findById / deleteById) 외에,
 * 사번(userId) 으로 모든 이력을 조회하는 {@link #findByUserId(String)} 와,
 * 등록 시 기간 중복 검사를 위한 {@link #findOverlappingActive} 만 제공합니다.</p>
 */
public interface AbsenceRepository extends JpaRepository<AbsenceEntity, Long> {

    /** 특정 사용자(사번)의 모든 부재 이력 (최근순) */
    @Query("select a from AbsenceEntity a where a.userId = :userId order by a.abscCretDttm desc")
    List<AbsenceEntity> findByUserId(@Param("userId") String userId);

    @Query("select a from AbsenceEntity a " +
            "where a.userId = :userId " +
            "  and a.abscCnceDttm is null " +
            "  and a.abscStarDttm <= :at " +
            "  and (a.abscEndDttm is null or a.abscEndDttm >= :at) " +
            "order by a.abscCretDttm desc")
    List<AbsenceEntity> findActiveAt(@Param("userId") String userId,
                                     @Param("at") Date at);

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
            "  and a.abscCnceDttm is null " +
            "  and a.abseId <> :excludeAbseId " +
            "  and ( :newAbscEndDttm is null or a.abscStarDttm <= :newAbscEndDttm ) " +
            "  and ( a.abscEndDttm is null or a.abscEndDttm >= :newAbscStarDttm )")
    List<AbsenceEntity> findOverlappingActive(@Param("userId") String userId,
                                               @Param("newAbscStarDttm") Date newAbscStarDttm,
                                               @Param("newAbscEndDttm") Date newAbscEndDttm,
                                               @Param("excludeAbseId") Long excludeAbseId);
}
