package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import jakarta.persistence.criteria.Expression;
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
      Long cursorTaskId,
      int pageSize,
      UserContext userContext) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    SortField sortField = SortField.from(request.getSortOrdrVal());
    CursorPosition cursor = findCursor(builder, cursorTaskId, sortField);
    if (cursorTaskId != null && cursor == null) {
      return new SearchResult(List.of(), 0);
    }

    CriteriaQuery<WorklistEntity> dataQuery = builder.createQuery(WorklistEntity.class);
    Root<WorklistEntity> worklist = dataQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance = fetchProcessInstance(worklist);
    List<Predicate> dataPredicates = new ArrayList<>(
        List.of(predicates(builder, worklist, instance, request, userContext)));
    if (cursor != null) {
      dataPredicates.add(cursorPredicate(builder, worklist, instance, sortField, cursor));
    }
    dataQuery.select(worklist)
        .where(dataPredicates.toArray(Predicate[]::new))
        .orderBy(sortOrders(builder, worklist, instance, sortField));

    TypedQuery<WorklistEntity> query = entityManager.createQuery(dataQuery);
    query.setMaxResults(pageSize);
    List<WorklistEntity> items = query.getResultList();

    if (cursor == null && items.size() < pageSize) {
      int totalCount = items.size();
      return new SearchResult(items, totalCount);
    }

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<WorklistEntity> countWorklist = countQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> countInstance =
        countWorklist.join("processInstance", JoinType.LEFT);
    countQuery.select(builder.count(countWorklist))
        .where(predicates(builder, countWorklist, countInstance, request, userContext));
    long totalCount = entityManager.createQuery(countQuery).getSingleResult();

    return new SearchResult(items, Math.toIntExact(totalCount));
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
    if (rows.isEmpty()) {
      return null;
    }
    return new CursorPosition(rows.get(0).get("sortValue", Date.class), cursorTaskId);
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
    predicates.add(accessPredicate(builder, worklist, userContext));
    predicates.add(builder.not(worklist.get("status").in("COMPLETED", "CANCELLED")));

    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBswrClsfCode());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addText(builder, predicates, instance.get("fncgBswrDvsnCode"), request.getFncgBswrDvsnCode());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("corrKey"), request.getLoanPcesMgmtNo());
    addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    addText(builder, predicates, worklist.get("trcTag"), request.getFncgBpmTaskTrcgNm());
    addText(builder, predicates, worklist.get("endpoint"), request.getHndrEmnb());
    addOrganization(builder, predicates, worklist, request.getFncgWndwOrgnCode());
    addRange(builder, predicates, worklist.get("startDate"), request.getStarDate(), request.getEndDate());
    addRange(
        builder,
        predicates,
        instance.get("laonHopeDate"),
        request.getHopeStarDate(),
        request.getHopeEndDate());
    return predicates.toArray(Predicate[]::new);
  }

  private static Predicate accessPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      UserContext userContext) {
    String userId = trimToNull(userContext == null ? null : userContext.getUserId());
    List<String> scopes = normalizedValues(userContext == null ? null : userContext.getScopes());
    List<String> groups = normalizedValues(userContext == null ? null : userContext.getGroups());

    Path<String> endpoint = worklist.get("endpoint");
    Path<String> assignGroup = worklist.get("assignGroup");
    Path<String> scope = worklist.get("scope");
    Predicate dispatch = builder.equal(worklist.get("dispatchOption"), 1);
    Predicate unclaimed = builder.isNull(endpoint);
    Predicate noAssignGroup = builder.or(
        builder.isNull(assignGroup),
        builder.equal(assignGroup, "null"));
    Predicate noScope = builder.or(
        builder.isNull(scope),
        builder.equal(scope, "null"));

    return builder.or(
        userId == null ? builder.disjunction() : builder.equal(endpoint, userId),
        in(builder, endpoint, scopes),
        builder.and(dispatch, unclaimed, noAssignGroup, in(builder, scope, groups)),
        builder.and(dispatch, unclaimed, noAssignGroup, in(builder, scope, scopes)),
        builder.and(
            dispatch,
            unclaimed,
            in(builder, assignGroup, groups),
            builder.or(noScope, in(builder, scope, scopes))));
  }

  private static Predicate in(
      CriteriaBuilder builder,
      Path<String> path,
      List<String> values) {
    return values.isEmpty() ? builder.disjunction() : path.in(values);
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

  private static void addOrganization(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Root<WorklistEntity> worklist,
      String expected) {
    String value = trimToNull(expected);
    if (value == null) {
      return;
    }

    Path<String> assignGroup = worklist.get("assignGroup");
    Path<String> scope = worklist.get("scope");
    Expression<String> trimmedGroup = builder.trim(assignGroup);
    Predicate hasGroup = builder.and(
        builder.isNotNull(assignGroup),
        builder.notEqual(trimmedGroup, ""));
    predicates.add(builder.or(
        builder.and(hasGroup, builder.equal(trimmedGroup, value)),
        builder.and(builder.not(hasGroup), builder.equal(builder.trim(scope), value))));
  }

  private static void addRange(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<Date> path,
      Date startInclusive,
      Date endInclusive) {
    if (startInclusive != null) {
      predicates.add(builder.greaterThanOrEqualTo(path, startInclusive));
    }
    if (endInclusive != null) {
      predicates.add(builder.lessThanOrEqualTo(path, endInclusive));
    }
  }

  private static Predicate cursorPredicate(
      CriteriaBuilder builder,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      SortField sortField,
      CursorPosition cursor) {
    Path<Long> taskId = worklist.get("taskId");
    if (sortField == SortField.TASK_ID) {
      return builder.lessThan(taskId, cursor.taskId());
    }

    Path<Date> sortPath = dateSortPath(worklist, instance, sortField);
    if (cursor.sortValue() == null) {
      return builder.and(
          builder.isNull(sortPath),
          builder.lessThan(taskId, cursor.taskId()));
    }

    return builder.or(
        builder.isNull(sortPath),
        builder.lessThan(sortPath, cursor.sortValue()),
        builder.and(
            builder.equal(sortPath, cursor.sortValue()),
            builder.lessThan(taskId, cursor.taskId())));
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
      case LOAN_HOPE_DATE -> instance.get("laonHopeDate");
      case STARTED_DATE -> instance.get("startedDate");
      case WORK_STARTED_DATE -> worklist.get("startDate");
      case TASK_ID -> throw new IllegalArgumentException("taskId is not a date sort field");
    };
  }

  private static List<String> normalizedValues(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(MyTodoSearchRepository::trimToNull)
        .filter(value -> value != null)
        .distinct()
        .toList();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public record SearchResult(List<WorklistEntity> items, int totalCount) {

    public SearchResult {
      items = List.copyOf(items);
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
      if ("fncgBpmTaskLstId".equals(value) || "taskId".equals(value)) {
        return TASK_ID;
      }
      return STARTED_DATE;
    }
  }
}
