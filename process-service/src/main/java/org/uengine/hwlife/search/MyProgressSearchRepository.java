package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.MyProgressRequest;
import org.uengine.kernel.Activity;
import org.uengine.webservices.worklist.DefaultWorkList;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public class MyProgressSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public MyProgressSearchRepository() {
  }

  MyProgressSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public SearchResult search(
      MyProgressRequest request,
      Long cursorId,
      int pageSize,
      String emnb) {
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
        List.of(predicates(builder, dataQuery, worklist, instance, request, emnb)));
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
        .where(predicates(builder, countQuery, countWorklist, countInstance, request, emnb));
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
      MyProgressRequest request,
      String emnb) {
    List<Predicate> predicates = new ArrayList<>();
    // 1) 인스턴스 진행중
    predicates.add(builder.equal(instance.get("status"), Activity.STATUS_RUNNING));
    // 2) 현재 단위업무 (조직진행현황·현재 워크아이템과 동일)
    predicates.add(builder.upper(worklist.get("status")).in(DefaultWorkList.WORKITEM_STATUS_NEW));
    // 3) 본인이 한 번이라도 완료 처리한 업무 (루트 인스턴스 기준, 서브프로세스 포함)
    predicates.add(involvementPredicate(builder, query, worklist, emnb));

    addRootDefId(builder, query, predicates, instance, request.getBswrDvsnVal());
    addText(builder, predicates, worklist.get("defId"), request.getFncgBpmPcesId());
    addText(builder, predicates, worklist.get("title"), request.getUworNm());
    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    addText(builder, predicates, instance.get("initGroupCd"), request.getFncgWndwOrgnCode());
    addDateRange(
        builder,
        predicates,
        instance.get("startedDate"),
        request.getRqstStarDate(),
        request.getRqstEndDate());
    return predicates.toArray(Predicate[]::new);
  }

  /**
   * 같은 루트 인스턴스에서 요청자 사번이 완료({@code COMPLETED})한 워크리스트가 있는지.
   * {@code coalesce(rootInstId, instId)} 로 서브프로세스까지 한 업무로 본다.
   */
  private static Predicate involvementPredicate(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      Root<WorklistEntity> worklist,
      String emnb) {
    String handler = trimToNull(emnb);
    if (handler == null) {
      return builder.disjunction();
    }
    Subquery<Long> involved = query.subquery(Long.class);
    Root<WorklistEntity> completed = involved.from(WorklistEntity.class);
    Expression<Long> currentRoot =
        builder.coalesce(worklist.get("rootInstId"), worklist.get("instId"));
    Expression<Long> completedRoot =
        builder.coalesce(completed.get("rootInstId"), completed.get("instId"));
    involved.select(completed.get("taskId"))
        .where(
            builder.equal(completedRoot, currentRoot),
            builder.equal(builder.upper(completed.get("status")), DefaultWorkList.WORKITEM_STATUS_COMPLETED),
            builder.equal(completed.get("endpoint"), handler));
    return builder.exists(involved);
  }

  /**
   * 서브프로세스 단위업무도 루트 프로세스 정의로 필터링한다.
   * {@code coalesce(instance.rootInstId, instance.instId)} 의 {@code defId == bswrDvsnVal}.
   */
  private static void addRootDefId(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      List<Predicate> predicates,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      String bswrDvsnVal) {
    String value = trimToNull(bswrDvsnVal);
    if (value == null) {
      return;
    }
    Subquery<Long> rootMatch = query.subquery(Long.class);
    Root<ProcessInstanceEntity> rootInstance = rootMatch.from(ProcessInstanceEntity.class);
    Expression<Long> rootInstId =
        builder.coalesce(instance.get("rootInstId"), instance.get("instId"));
    rootMatch.select(rootInstance.get("instId"))
        .where(
            builder.equal(rootInstance.get("instId"), rootInstId),
            builder.equal(rootInstance.get("defId"), value));
    predicates.add(builder.exists(rootMatch));
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

  private String sortKey(MyProgressRequest request) {
    String value = trimToNull(request.getSortOrdrVal());
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
    return "taskId".equals(trimToNull(sortKey));
  }

  private static void addText(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<String> path,
      String expected) {
    String value = trimToNull(expected);
    if (value != null) {
      predicates.add(builder.equal(path, value));
    }
  }

  private static void addDateRange(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<Date> path,
      Date startInclusive,
      Date endInclusive) {
    // 시작일: yyyyMMdd 00:00:00.000 이상
    if (startInclusive != null) {
      predicates.add(builder.greaterThanOrEqualTo(path, startOfDay(startInclusive)));
    }
    // 종료일: yyyyMMdd 23:59:59.999 이하 (= 익일 00:00:00 미만)
    if (endInclusive != null) {
      predicates.add(builder.lessThan(path, startOfNextDay(endInclusive)));
    }
  }

  private static Date startOfDay(Date value) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
    calendar.setTime(value);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

  private static Date startOfNextDay(Date value) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
    calendar.setTime(startOfDay(value));
    calendar.add(Calendar.DAY_OF_MONTH, 1);
    return calendar.getTime();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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
