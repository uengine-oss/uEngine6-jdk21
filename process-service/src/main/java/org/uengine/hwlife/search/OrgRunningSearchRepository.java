package org.uengine.hwlife.search;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.OrgRunningRequest;

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
public class OrgRunningSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public OrgRunningSearchRepository() {
  }

  OrgRunningSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public SearchResult search(OrgRunningRequest request, Long cursorTaskId, int pageSize) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    SortField sortField = SortField.from(request.getSortOrdrVal());
    CursorPosition cursor = findCursor(builder, cursorTaskId, sortField);
    if (cursorTaskId != null && cursor == null) {
      return new SearchResult(List.of(), 0, null);
    }

    CriteriaQuery<WorklistEntity> dataQuery = builder.createQuery(WorklistEntity.class);
    Root<WorklistEntity> worklist = dataQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance = fetchProcessInstance(worklist);
    List<Predicate> dataPredicates =
        new ArrayList<>(List.of(predicates(builder, worklist, instance, request)));
    if (cursor != null) {
      dataPredicates.add(
          cursorPredicate(builder, worklist, instance, sortField, cursor));
    }
    dataQuery.select(worklist)
        .where(dataPredicates.toArray(Predicate[]::new))
        .orderBy(sortOrders(builder, worklist, instance, sortField));

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
      return new SearchResult(items, items.size(), null);
    }

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<WorklistEntity> countWorklist = countQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> countInstance =
        countWorklist.join("processInstance", JoinType.LEFT);
    countQuery.select(builder.count(countWorklist))
        .where(predicates(builder, countWorklist, countInstance, request));
    long totalCount = entityManager.createQuery(countQuery).getSingleResult();
    return new SearchResult(items, Math.toIntExact(totalCount), nextKey);
  }

  private CursorPosition findCursor(
      CriteriaBuilder builder,
      Long cursorTaskId,
      SortField sortField) {
    if (cursorTaskId == null) {
      return null;
    }
    if (sortField == SortField.TASK_ID) {
      return new CursorPosition(null, cursorTaskId);
    }

    CriteriaQuery<Tuple> cursorQuery = builder.createTupleQuery();
    Root<WorklistEntity> worklist = cursorQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance =
        worklist.join("processInstance", JoinType.LEFT);
    cursorQuery.multiselect(
        worklist.get("taskId").alias("taskId"),
        dateSortPath(worklist, instance, sortField).alias("sortValue"))
        .where(builder.equal(worklist.get("taskId"), cursorTaskId));
    List<Tuple> rows = entityManager.createQuery(cursorQuery)
        .setMaxResults(1)
        .getResultList();
    return rows.isEmpty()
        ? null
        : new CursorPosition(rows.get(0).get("sortValue", Date.class), cursorTaskId);
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
      OrgRunningRequest request) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(builder.upper(worklist.get("status")).in("NEW", "RUNNING"));
    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBswrClsfCode());
    addText(
        builder,
        predicates,
        instance.get("fncgBswrDvsnCode"),
        request.getFncgVswrDvsnCode());
    addText(builder, predicates, worklist.get("trcTag"), request.getFncgBpmTaskTrcgNm());
    addRange(
        builder,
        predicates,
        worklist.get("startDate"),
        request.getRqstStarDttm(),
        request.getRqstEndDttm());
    addText(
        builder,
        predicates,
        instance.get("fncgSuptTrgtDvsnCode"),
        request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addText(
        builder,
        predicates,
        instance.get("fncgMneyUsagClsfCode"),
        request.getFncgMneyUsagClsfCode());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addOrganization(builder, predicates, worklist, instance, request);
    return predicates.toArray(Predicate[]::new);
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

  private static void addRange(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<Date> path,
      LocalDateTime startInclusive,
      LocalDateTime endInclusive) {
    if (startInclusive != null) {
      predicates.add(builder.greaterThanOrEqualTo(path, toDate(startInclusive)));
    }
    if (endInclusive != null) {
      predicates.add(builder.lessThanOrEqualTo(path, toDate(endInclusive)));
    }
  }

  private static void addOrganization(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      OrgRunningRequest request) {
    String organizationCode = trimToNull(request.getFncgWndwOrgnCode());
    if (organizationCode == null) {
      return;
    }
    if ("Y".equalsIgnoreCase(trimToNull(request.getRqstDvsnCode()))) {
      predicates.add(builder.equal(instance.get("initComCd"), organizationCode));
      return;
    }

    Path<String> scope = worklist.get("scope");
    predicates.add(builder.equal(builder.trim(scope), organizationCode));
  }

  private static Predicate cursorPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      SortField sortField,
      CursorPosition cursor) {
    Path<Long> taskId = worklist.get("taskId");
    if (sortField == SortField.TASK_ID) {
      return compareInclusive(builder, taskId, cursor.taskId());
    }
    Path<Date> sortPath = dateSortPath(worklist, instance, sortField);
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

  private static List<jakarta.persistence.criteria.Order> sortOrders(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      SortField sortField) {
    if (sortField == SortField.TASK_ID) {
      return List.of(builder.desc(worklist.get("taskId")));
    }
    Path<Date> sortPath = dateSortPath(worklist, instance, sortField);
    return List.of(
        builder.asc(builder.selectCase().when(builder.isNull(sortPath), 1).otherwise(0)),
        builder.desc(sortPath),
        builder.desc(worklist.get("taskId")));
  }

  private static Path<Date> dateSortPath(
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      SortField sortField) {
    return switch (sortField) {
      case LOAN_HOPE_DATE -> instance.get("loanHopeDate");
      case STARTED_DATE -> instance.get("startedDate");
      case WORK_STARTED_DATE -> worklist.get("startDate");
      case TASK_ID -> throw new IllegalArgumentException("taskId is not a date sort field");
    };
  }

  private static Date toDate(LocalDateTime value) {
    return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
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

  private enum SortField {
    LOAN_HOPE_DATE,
    STARTED_DATE,
    WORK_STARTED_DATE,
    TASK_ID;

    private static SortField from(String propertyName) {
      String value = trimToNull(propertyName);
      if ("loanHopeDate".equals(value)) {
        return LOAN_HOPE_DATE;
      }
      if ("uworStarDttm".equals(value) || "startDate".equals(value)) {
        return WORK_STARTED_DATE;
      }
      if ("fncgBpmtaskLstId".equals(value)
          || "fncgBpmTaskLstId".equals(value)
          || "taskId".equals(value)) {
        return TASK_ID;
      }
      return STARTED_DATE;
    }
  }

}
