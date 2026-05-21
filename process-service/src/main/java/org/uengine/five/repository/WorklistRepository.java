package org.uengine.five.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
//import org.metaworks.multitenancy.persistence.MultitenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.uengine.five.entity.WorklistEntity;

/**
 * Created by uengine on 2017. 6. 19..
 */
@RepositoryRestResource(collectionResourceRel = "worklist", path = "worklist")
public interface WorklistRepository extends JpaRepository<WorklistEntity, Long> {

    // @Query("select wl from WorklistEntity wl where (wl.endpoint =
    // ?#{loggedUserId} or wl.endpoint in ?#{loggedUserScopes}) and (wl.status =
    // 'NEW' or wl.status = 'DRAFT')")

    /**
     * ToDo 정의
     * - 기본: endpoint 가 나(principal.userId) 이거나, endpoint 가 내 scope(roles) 중 하나인 workitem
     * - 추가: dispatchOption = 1(경합/RACING) 인 경우 roleName 이 내 scope(roles)와 일치해도 노출
     * - 상태: COMPLETED / CANCELLED 는 제외
     */
//     @Query("select wl from WorklistEntity wl where (wl.endpoint = ?#{principal.userId} or wl.endpoint in ?#{principal.scopes}) and (wl.status != 'COMPLETED') ")
    @Query("select wl from WorklistEntity wl " +
            "where (" +
            "   (wl.endpoint = ?#{principal.userId} or wl.endpoint in ?#{principal.scopes})" +
            "   or (wl.dispatchOption = 1 and wl.endpoint is null and (wl.assignGroup is null or wl.assignGroup = 'null') and wl.scope in ?#{principal.groups})" +
            "   or (wl.dispatchOption = 1 and wl.endpoint is null and (wl.assignGroup is null or wl.assignGroup = 'null') and wl.scope in ?#{principal.scopes})" +
            "   or (wl.dispatchOption = 1 and wl.endpoint is null and wl.assignGroup in ?#{principal.groups} and (wl.scope is null or wl.scope = 'null' or wl.scope in ?#{principal.scopes}))" +
            ") and (wl.status != 'COMPLETED') ")
    public List<WorklistEntity> findToDo();

    // @Query("select wl from WorklistEntity wl where (wl.endpoint =
    // ?#{principal.userId} or wl.endpoint in ?#{principal.scopes}) and (wl.status =
    // 'IN_PROGRESS')")
    // public List<WorklistEntity> findInProgress();

    // @Query("select wl from WorklistEntity wl where (wl.endpoint =
    // ?#{principal.userId} or wl.endpoint in ?#{principal.scopes}) and (wl.status =
    // 'PENDING')")
    // public List<WorklistEntity> findPending();

    // @Query("select wl from WorklistEntity wl where (wl.endpoint = ?#{principal.userId} or wl.endpoint in ?#{principal.scopes}) and (wl.status = 'COMPLETED')")
    // public List<WorklistEntity> findCompleted(Pageable pageable);

    @Query("select pi from WorklistEntity pi " +
            "where (pi.endpoint = ?#{principal.userId} or pi.endpoint in ?#{principal.scopes}) " +
            "and (pi.status = 'COMPLETED')" )
    Page<WorklistEntity> findCompleted(Pageable pageable);

    @Query("select wl from WorklistEntity wl where (wl.rootInstId = :rootInstId and wl.status = 'COMPLETED') order by wl.endDate ")
    public List<WorklistEntity> findWorkListByInstId(@Param(value = "rootInstId") Long rootInstId);

    @Query("select wl from WorklistEntity wl where (wl.rootInstId = :rootInstId and (wl.status = 'NEW' or wl.status = 'RUNNING')) order by wl.endDate ")
    public List<WorklistEntity> findCurrentWorkItemByInstId(@Param(value = "rootInstId") Long rootInstId);

    /**
     * 경합(DispatchOption=1) 등으로 endpoint가 비어있는 workitem들을,
     * 특정 사용자가 claim 했을 때 동일 role/scope/assignType 그룹으로 함께 소유자 세팅하기 위해 사용.
     *
     * rootInstId는 일부 레코드에서 null일 수 있어 instId로 fallback 합니다.
     */
    @Query("select wl from WorklistEntity wl " +
            "where ( (wl.rootInstId = :rootInstId) or (wl.rootInstId is null and wl.instId = :rootInstId) ) " +
            "  and wl.roleName = :roleName " +
            "  and ( (:scope is null and wl.scope is null) or (:scope is not null and wl.scope = :scope) ) " +
            "  and ( (:assignGroup is null and wl.assignGroup is null) or (:assignGroup is not null and wl.assignGroup = :assignGroup) ) " +
            "  and wl.assignType = :assignType " +
            "  and (wl.status = 'NEW' or wl.status = 'RUNNING') " +
            "  and ( (:endpoint is null and wl.endpoint is null) or (:endpoint is not null and wl.endpoint = :endpoint) ) ")
    public List<WorklistEntity> findSiblingsForClaimState(@Param("rootInstId") Long rootInstId,
            @Param("roleName") String roleName,
            @Param("scope") String scope,
            @Param("assignGroup") String assignGroup,
            @Param("assignType") Integer assignType,
            @Param("endpoint") String endpoint);

    /**
     * 나의 결재함(My Approval) 동적 검색.
     *
     * <p>"결재함" 의 정의: {@code approvalType} 컬럼이 채워진 워크아이템.
     * (= 결재 액션이 매핑된 task 만 노출)</p>
     *
     * <ul>
     *   <li>정렬: 시작일 내림차순 (최근이 1번째).</li>
     *   <li>고정 필터:
     *     <ul>
     *       <li>{@code wl.approvalType IS NOT NULL} — 결재함 정의</li>
     *     </ul>
     *   </li>
     *   <li>옵션 파라미터 (모두 nullable, null 이면 해당 조건 무시):
     *     <ul>
     *       <li>endpoint   : 담당자 (자기 결재함 한정)</li>
     *       <li>defId      : 업무구분 (프로세스 정의 ID)</li>
     *       <li>absTrcTag  : 단위업무 태그</li>
     *       <li>status     : 업무 상태</li>
     *       <li>startDate  : 시작일자 (이 날짜 이후, inclusive)</li>
     *       <li>endDate    : 종료일자 (이 날짜 이전, inclusive — NULL 종료일은 제외)</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * 선점/그룹/롤 기반 노출 조건은 추후 OR 분기로 확장 예정.
     */
    @Query("select wl from WorklistEntity wl " +
            "where wl.approvalType is not null " +
            "and (:endpoint is null or wl.endpoint = :endpoint) " +
            "and (:defId is null or wl.defId = :defId) " +
            "and (:absTrcTag is null or wl.absTrcTag = :absTrcTag) " +
            "and (:status is null or wl.status = :status) " +
            "and (:startDate is null or wl.startDate >= :startDate) " +
            "and (:endDate is null or :endDate >= wl.endDate) " +
            "order by wl.startDate desc")
    Page<WorklistEntity> findMyApproval(
            @Param("endpoint") String endpoint,
            @Param("defId") String defId,
            @Param("activityId") String absTrcTag,
            @Param("status") String status,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Param("startDate") Date startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Param("endDate") Date endDate,
            Pageable pageable);

    /**
     * 기관(조직) 코드·요청 타입·업무구분으로 워크리스트 검색. 결재함({@code approvalType}) 구분 없이 전체 워크아이템 대상.
     *
     * <ul>
     *   <li>{@code requestYn = 'Y'} — 요청기관 {@code pi.initComCd}</li>
     *   <li>{@code requestYn != 'Y'} — 진행기관 {@code wl.group}</li>
     *   <li>{@code defId} — 업무구분 (null 이면 무시)</li>
     * </ul>
     */
    @Query("select wl from WorklistEntity wl join wl.processInstance pi " +
            "where (:groupCode is null " +
            "   or ((:requestYn = 'Y' and pi.initComCd = :groupCode) " +
            "       or (:requestYn <> 'Y' and wl.group = :groupCode))) " +
            "and (:defId is null or wl.defId = :defId) " +
            "order by wl.startDate desc")
    Page<WorklistEntity> findByGroupCode(
            @Param("groupCode") String groupCode,
            @Param("requestYn") String requestYn,
            @Param("defId") String defId,
            Pageable pageable);

    // // // TEST
    // @Query("select wl from WorklistEntity wl")
    // public List<WorklistEntity> findAll();

}
        