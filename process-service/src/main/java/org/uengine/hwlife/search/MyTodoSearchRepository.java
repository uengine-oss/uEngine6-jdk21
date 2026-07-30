package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.stereotype.Repository;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.MyTodoRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
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
      UserContext userContext) {
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
        List.of(predicates(builder, worklist, instance, request, userContext)));
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
        .where(predicates(builder, countWorklist, countInstance, request, userContext));
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
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      MyTodoRequest request,
      UserContext userContext) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(accessPredicate(builder, worklist, request, userContext));
    predicates.add(builder.not(worklist.get("status").in("COMPLETED", "CANCELLED")));

    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addText(builder, predicates, instance.get("fncgBswrDvsnCode"), request.getFncgBswrDvsnCode());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("corrKey"), request.getLoanPcesMgmtNo());
    addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    addText(builder, predicates, worklist.get("trcTag"), request.getFncgBpmTaskTrcgNm());
    addDateRange(
        builder,
        predicates,
        worklist.get("startDate"),
        request.getStartDate(),
        request.getEndDate());
    addDateRange(
        builder,
        predicates,
        instance.get("loanHopeDate"),
        request.getHopeStartDate(),
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

  private static Predicate accessPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      MyTodoRequest request,
      UserContext userContext) {
    String requestedHandler = trimToNull(request.getHndrEmnb());
    String requestedOrganization = trimToNull(request.getFncgWndwOrgnCode());

    Path<String> endpoint = worklist.get("endpoint");
    Path<String> groupCd = worklist.get("groupCd");
    Predicate dispatch = builder.equal(worklist.get("dispatchOption"), 1);
    Predicate unclaimed = builder.isNull(endpoint);
    Predicate requestHandler =
        requestedHandler == null ? builder.disjunction() : builder.equal(endpoint, requestedHandler);
    Predicate requestClaimable =
        requestedOrganization == null
            ? builder.disjunction()
            : builder.and(dispatch, unclaimed, builder.equal(builder.trim(groupCd), requestedOrganization));

    return builder.or(
        requestHandler,
        requestClaimable);
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
    if (startInclusive != null) {
      predicates.add(builder.greaterThanOrEqualTo(path, startOfDay(startInclusive)));
    }
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
