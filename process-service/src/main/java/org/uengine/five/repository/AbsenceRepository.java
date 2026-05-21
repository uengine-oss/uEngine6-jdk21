package org.uengine.five.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.uengine.five.entity.AbsenceEntity;

/**
 * 부재자/대결자 설정 저장소.
 *
 * <p>JpaRepository 기본 메서드 (save / findById / deleteById) 외에,
 * 사번(userId) 으로 모든 이력을 조회하는 {@link #findByUserId(String)} 와,
 * 등록 시 기간 중복 검사를 위한 {@link #findOverlappingActive} 만 제공합니다.</p>
 */
public interface AbsenceRepository extends JpaRepository<AbsenceEntity, Long> {

    /** 특정 사용자(사번)의 모든 부재 이력 (최근순) */
    @Query("select a from AbsenceEntity a where a.userId = :userId order by a.createdDate desc")
    List<AbsenceEntity> findByUserId(@Param("userId") String userId);

    /**
     * 동일 userId 로 기간이 겹치는 활성 부재가 존재하는지 검사 (등록 시 중복 방지용).
     *
     * @param userId    부재자
     * @param newStart  새로 등록할 시작일
     * @param newEnd    새로 등록할 종료일 (null 이면 무한대로 간주 → 어떤 활성이든 충돌)
     * @param excludeId 예약 필드 (등록 시 -1L 전달)
     */
    @Query("select a from AbsenceEntity a " +
            "where a.userId = :userId " +
            "  and a.status = 'ACTIVE' " +
            "  and a.absenceId <> :excludeId " +
            "  and ( :newEnd is null or a.startDate <= :newEnd ) " +
            "  and ( a.endDate is null or a.endDate >= :newStart )")
    List<AbsenceEntity> findOverlappingActive(@Param("userId") String userId,
                                               @Param("newStart") Date newStart,
                                               @Param("newEnd") Date newEnd,
                                               @Param("excludeId") Long excludeId);
}
