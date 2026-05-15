package org.uengine.five.repository;

import java.util.Date;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.framework.ProcessTransactional;

/**
 * H2/PostgreSQL용 ProcessInstance Repository (oracle 외 모든 프로필).
 *
 * 주의사항:
 * 1) PostgreSQL은 타입 없는 NULL 파라미터를 LIKE/CONCAT 안에서 그대로 쓰면
 *    "operator does not exist: character varying ~~ bytea" 로 쿼리 자체가 실패한다.
 *    → LIKE 패턴에 들어가는 String 파라미터는 COALESCE(:x, '') 로 감싸 타입(text)을 확정한다.
 *    (런타임에는 ':x is null' 분기로 단락되므로 검색 결과에는 영향 없음)
 * 2) eventHandler/subProcess 는 boolean 컬럼이므로 String 파라미터와 직접 '=' 비교하면
 *    Hibernate 6 가 SemanticException(String vs Boolean) 으로 거부한다.
 *    → 'true'/'false'/'1'/'0' 문자열 비교 패턴(findByName 과 동일)으로 처리한다.
 * 3) rolePattern/namePattern: postgres/H2 경로에서는 실제 사용처가 없고(Admin 페이지가 안 넘김)
 *    기존 정규식 로직도 버그가 있었으므로, 안전한 LIKE 필터로 단순화한다.
 *    (Spring Data JPA 는 @Param 으로 선언한 named parameter 가 쿼리에서 안 쓰이면 부팅에 실패하므로
 *     시그니처 호환을 위해 쿼리에서 참조는 유지한다.)
 * 4) 페이지 count 쿼리는 GROUP BY 없는 별도 countQuery 로 명시한다.
 */
@Profile("!oracle")
@ProcessTransactional
@RepositoryRestResource(collectionResourceRel = "instances", path = "instances")
public interface ProcessInstanceRepositoryH2 extends ProcessInstanceRepository {

    // PostgreSQL 호환:
    // 1) String 파라미터의 "비어있음" 체크는 COALESCE(:x,'')='' 패턴으로 통일.
    //    `(:x is null or ...)` 의 좌측 `:x is null` 은 PG 의 parameter 타입 추론에 실패해
    //    JDBC null 바인딩 시 bytea 로 잘못 추론 → prepare 단계 실패. COALESCE(:x,'') 는
    //    ''(text) 와 결합되므로 PG 가 :x 를 즉시 text 로 확정한다.
    // 2) Long/Date/Boolean 같은 비-문자열 파라미터의 `is null` 체크는 cast(:x as <type>)
    //    로 감싸 JPQL 단계에서 명시적 타입을 부여한다. (PG가 같은 쿼리 내 다른 `?` 위치의
    //    컬럼 비교로 타입을 unify 해주지 않으므로 `is null` 위치에서 따로 못 알려주면 실패)
    String FILTER_I_CAN_SEE_WHERE =
            "where 1=1 " +
            "and (cast(:instId as long) is null or pi.instId = :instId) " +
            "and (COALESCE(:defId, '') = '' or pi.defId like CONCAT('%', :defId, '%')) " +
            "and (COALESCE(:status, '') = '' or pi.status = :status) " +
            "and (COALESCE(:eventHandler, '') = '' " +
            "     or (pi.eventHandler = true  and (:eventHandler = 'true'  or :eventHandler = '1')) " +
            "     or (pi.eventHandler = false and (:eventHandler = 'false' or :eventHandler = '0'))) " +
            "and (COALESCE(:name, '') = '' or pi.name like CONCAT('%', :name, '%')) " +
            "and (cast(:startedDate as date) is null or pi.startedDate >= :startedDate) " +
            "and (cast(:finishedDate as date) is null or pi.finishedDate <= :finishedDate) " +
            "and (cast(:subProcess as boolean) is null or :subProcess = pi.subProcess) " +
            "and (COALESCE(:initEp, '') = '' or pi.initEp like CONCAT('%', :initEp, '%')) " +
            "and (COALESCE(:currEp, '') = '' or pi.currEp like CONCAT('%', :currEp, '%')) " +
            "and (COALESCE(:prevCurrEp, '') = '' or pi.prevCurrEp like CONCAT('%', :prevCurrEp, '%')) " +
            "and (COALESCE(:rolePattern, '') = '' or pi.currEp like CONCAT('%', :rolePattern, '%')) " +
            "and (COALESCE(:namePattern, '') = '' or pi.currEp like CONCAT('%', :namePattern, '%')) ";

    @Override
    @Query(value = "select distinct pi from ProcessInstanceEntity pi " + FILTER_I_CAN_SEE_WHERE
            + "order by pi.startedDate desc",
            countQuery = "select count(distinct pi) from ProcessInstanceEntity pi " + FILTER_I_CAN_SEE_WHERE)
    Page<ProcessInstanceEntity> findFilterICanSee(
            @Param("defId") String defId,
            @Param("instId") Long instId,
            @Param("status") String status,
            @Param("eventHandler") String eventHandler,
            @Param("name") String name,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Param("startedDate") Date startedDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Param("finishedDate") Date finishedDate,
            @Param("initEp") String initEp,
            @Param("prevCurrEp") String prevCurrEp,
            @Param("currEp") String currEp,
            @Param("subProcess") Boolean subProcess,
            @Param("rolePattern") String rolePattern,
            @Param("namePattern") String namePattern,
            Pageable pageable);

    @Override
    @Query("select pi from ProcessInstanceEntity pi where (:name is null or pi.name like CONCAT('%',:name,'%')) and (:status is null or pi.status = :status) and (:startedDate is null or pi.startedDate >= :startedDate) and (:finishedDate is null or :finishedDate >= pi.finishedDate ) and (:subProcess is null or :subProcess = '' or (pi.subProcess = true and (:subProcess = 'true' or :subProcess = '1')) or (pi.subProcess = false and (:subProcess = 'false' or :subProcess = '0'))) order by pi.startedDate desc")
    Page<ProcessInstanceEntity> findByName(@Param("name") String name, @Param("status") String status,
            @Param("startedDate") String startedDate, @Param("finishedDate") String finishedDate,
            @Param("subProcess") String subProcess, Pageable pageable);
}
