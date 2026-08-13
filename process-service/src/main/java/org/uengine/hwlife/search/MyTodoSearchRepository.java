package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.MyTodoRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class MyTodoSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public MyTodoSearchRepository() {
  }

  MyTodoSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public SearchResult search(
      MyTodoRequest request,
      Long cursorId,
      int pageSize,
      String emnb,
      String belnOrgnCode) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    String sortKey = sortKey(request);
    CursorPosition cursor = findCursor(builder, cursorId, sortKey);
    if (cursorId != null && cursor == null) {
      return new SearchResult(List.of(), 0, null);
    }

    CriteriaQuery<WorklistEntity> dataQuery = builder.createQuery(WorklistEntity.class);
    Root<WorklistEntity> worklist = dataQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance = fetchProcessInstance(worklist);
    List<Predicate> dataPredicates = new ArrayList<>(
        List.of(predicates(builder, dataQuery, worklist, instance, request, emnb, belnOrgnCode)));
    // nextKey(taskId)는 정렬·비즈니스 필터가 아니라, 정렬된 결과의 페이지 커서다.
    if (cursor != null) {
      dataPredicates.add(cursorPredicate(builder, worklist, instance, sortKey, cursor));
    }
    dataQuery.select(worklist)
        .where(dataPredicates.toArray(Predicate[]::new))
        .orderBy(sortOrders(builder, worklist, instance, sortKey));

    TypedQuery<WorklistEntity> query = entityManager.createQuery(dataQuery);
    query.setMaxResults(pageSize + 1);
    List<WorklistEntity> fetchedItems = query.getResultList();
    String nextKey = fetchedItems.size() > pageSize
        ? String.valueOf(fetchedItems.get(pageSize).getTaskId())
        : null;
    List<WorklistEntity> items = fetchedItems.size() > pageSize
        ? fetchedItems.subList(0, pageSize)
        : fetchedItems;

    if (cursor == null && nextKey == null) {
      int totalCount = items.size();
      return new SearchResult(items, totalCount, null);
    }

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<WorklistEntity> countWorklist = countQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> countInstance =
        countWorklist.join("processInstance", JoinType.LEFT);
    countQuery.select(builder.count(countWorklist))
        .where(predicates(builder, countQuery, countWorklist, countInstance, request, emnb, belnOrgnCode));
    long totalCount = entityManager.createQuery(countQuery).getSingleResult();

    return new SearchResult(items, Math.toIntExact(totalCount), nextKey);
  }

  private CursorPosition findCursor(
      CriteriaBuilder builder,
      Long cursorId,
      String sortKey) {
    if (cursorId == null) {
      return null;
    }
    if (isTaskIdSort(sortKey)) {
      return new CursorPosition(null, cursorId);
    }

    CriteriaQuery<Tuple> cursorQuery = builder.createTupleQuery();
    Root<WorklistEntity> worklist = cursorQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance =
        worklist.join("processInstance", JoinType.LEFT);
    cursorQuery.multiselect(
            worklist.get("taskId").alias("taskId"),
            dateSortPath(worklist, instance, sortKey).alias("sortValue"))
        .where(builder.equal(worklist.get("taskId"), cursorId));
    List<Tuple> rows = entityManager.createQuery(cursorQuery)
        .setMaxResults(1)
        .getResultList();
    return rows.isEmpty()
        ? null
        : new CursorPosition(rows.get(0).get("sortValue", Date.class), cursorId);
  }

  @SuppressWarnings("unchecked")
  private static Join<WorklistEntity, ProcessInstanceEntity> fetchProcessInstance(
      Root<WorklistEntity> worklist) {
    Fetch<WorklistEntity, ProcessInstanceEntity> fetch =
        worklist.fetch("processInstance", JoinType.LEFT);
    return (Join<WorklistEntity, ProcessInstanceEntity>) fetch;
  }

  private static Predicate[] predicates(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      MyTodoRequest request,
      String emnb,
      String belnOrgnCode) {
    List<Predicate> predicates = new ArrayList<>();
    // 1) 진행중(NEW) 건
    predicates.add(builder.equal(worklist.get("status"), "NEW"));
    // 2) 본인 할당 건 OR 3) 소속기관 선점 미선점 건
    predicates.add(accessPredicate(builder, worklist, emnb, belnOrgnCode));

    // root_inst_id 기준 루트 인스턴스 def_id == bswrDvsnVal
    SearchPredicates.addRootDefId(builder, query, predicates, instance, request.getBswrDvsnVal());
    // 현재 단위업무 defId == fncgBpmPcesId
    SearchPredicates.addText(builder, predicates, worklist.get("defId"), request.getFncgBpmPcesId());
    // 단위업무명: worklist.title == uworNm
    SearchPredicates.addText(builder, predicates, worklist.get("title"), request.getUworNm());
    SearchPredicates.addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    SearchPredicates.addText(builder, predicates, instance.get("custId"), request.getCustId());
    SearchPredicates.addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    SearchPredicates.addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    SearchPredicates.addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    SearchPredicates.addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    // 요청기관 필터 (fncgWndwOrgnCode → bpm_procinst.init_group_cd)
    SearchPredicates.addText(builder, predicates, instance.get("initGroupCd"), request.getFncgWndwOrgnCode());
    SearchDateRanges.addInclusive(
        builder,
        predicates,
        worklist.get("startDate"),
        request.getStarDate(),
        request.getEndDate());
    SearchDateRanges.addInclusive(
        builder,
        predicates,
        instance.get("loanHopeDate"),
        request.getHopeStarDate(),
        request.getHopeEndDate());
    return predicates.toArray(Predicate[]::new);
  }

  private Predicate cursorPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      String sortKey,
      CursorPosition cursor) {
    Path<Long> taskId = worklist.get("taskId");
    if (isTaskIdSort(sortKey)) {
      return compareInclusive(builder, taskId, cursor.taskId());
    }
    Path<Date> sortPath = dateSortPath(worklist, instance, sortKey);
    if (cursor.sortValue() == null) {
      return builder.and(
          builder.isNull(sortPath),
          compareInclusive(builder, taskId, cursor.taskId()));
    }
    return builder.or(
        builder.isNull(sortPath),
        compare(builder, sortPath, cursor.sortValue()),
        builder.and(
            builder.equal(sortPath, cursor.sortValue()),
            compareInclusive(builder, taskId, cursor.taskId())));
  }

  private static <T extends Comparable<? super T>> Predicate compare(
      CriteriaBuilder builder,
      Path<T> path,
      T cursorValue) {
    return builder.lessThan(path, cursorValue);
  }

  private static <T extends Comparable<? super T>> Predicate compareInclusive(
      CriteriaBuilder builder,
      Path<T> path,
      T cursorValue) {
    return builder.lessThanOrEqualTo(path, cursorValue);
  }

  private List<jakarta.persistence.criteria.Order> sortOrders(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      String sortKey) {
    if (isTaskIdSort(sortKey)) {
      return List.of(builder.desc(worklist.get("taskId")));
    }
    Path<Date> sortPath = dateSortPath(worklist, instance, sortKey);
    return List.of(
        builder.asc(builder.selectCase().when(builder.isNull(sortPath), 1).otherwise(0)),
        builder.desc(sortPath),
        builder.desc(worklist.get("taskId")));
  }

  private Path<Date> dateSortPath(
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      String sortKey) {
    if (isTaskIdSort(sortKey)) {
      throw new IllegalArgumentException("taskId is not a date sort field");
    }
    if (hasAttribute(ProcessInstanceEntity.class, sortKey)) {
      return instance.get(sortKey);
    }
    if (hasAttribute(WorklistEntity.class, sortKey)) {
      return worklist.get(sortKey);
    }
    throw new IllegalArgumentException("Unsupported sortOrdrVal: " + sortKey);
  }

  private String sortKey(MyTodoRequest request) {
    String value = SearchPredicates.trimToNull(request.getSortOrdrVal());
    if (value == null) {
      return "taskId";
    }
    if (isTaskIdSort(value)
        || hasAttribute(ProcessInstanceEntity.class, value)
        || hasAttribute(WorklistEntity.class, value)) {
      return value;
    }
    return "taskId";
  }

  private boolean hasAttribute(Class<?> entityType, String attributeName) {
    try {
      entityManager.getMetamodel().entity(entityType).getAttribute(attributeName);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static boolean isTaskIdSort(String sortKey) {
    return "taskId".equals(SearchPredicates.trimToNull(sortKey));
  }

  /**
   * 나의 업무함 접근 조건.
   * <ul>
   *   <li>본인 할당: {@code endpoint == emnb}</li>
   *   <li>기관 선점 미선점: {@code dispatchOption == 1 AND groupCd == belnOrgnCode AND endpoint IS NULL}</li>
   * </ul>
   */
  private static Predicate accessPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      String emnb,
      String belnOrgnCode) {
    String handler = SearchPredicates.trimToNull(emnb);
    String organization = SearchPredicates.trimToNull(belnOrgnCode);

    Path<String> endpoint = worklist.get("endpoint");
    Path<String> groupCd = worklist.get("groupCd");
    Predicate assignedToMe =
        handler == null ? builder.disjunction() : builder.equal(endpoint, handler);
    Predicate claimable =
        organization == null
            ? builder.disjunction()
            : builder.and(
                builder.equal(worklist.get("dispatchOption"), 1),
                builder.isNull(endpoint),
                builder.equal(builder.trim(groupCd), organization));

    return builder.or(assignedToMe, claimable);
  }

  public record SearchResult(List<WorklistEntity> items, int totalCount, String nextKey) {

    public SearchResult {
      items = List.copyOf(items);
    }

    public SearchResult(List<WorklistEntity> items, int totalCount) {
      this(items, totalCount, null);
    }
  }

  private record CursorPosition(Date sortValue, Long taskId) {
  }

}
