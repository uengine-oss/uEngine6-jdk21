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
      Long cursorInstId,
      int pageSize,
      UserContext userContext) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();

    CriteriaQuery<WorklistEntity> dataQuery = builder.createQuery(WorklistEntity.class);
    Root<WorklistEntity> worklist = dataQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance = fetchProcessInstance(worklist);
    List<Predicate> dataPredicates = new ArrayList<>(
        List.of(predicates(builder, worklist, instance, request, userContext)));
    if (cursorInstId != null) {
      dataPredicates.add(builder.lessThanOrEqualTo(worklist.get("instId"), cursorInstId));
    }
    dataQuery.select(worklist)
        .where(dataPredicates.toArray(Predicate[]::new))
        .orderBy(
            builder.desc(worklist.get("instId")),
            builder.desc(worklist.get("taskId")));

    TypedQuery<WorklistEntity> query = entityManager.createQuery(dataQuery);
    query.setMaxResults(pageSize + 1);
    List<WorklistEntity> fetchedItems = query.getResultList();
    String nextKey = fetchedItems.size() > pageSize
        ? String.valueOf(fetchedItems.get(pageSize).getInstId())
        : null;
    List<WorklistEntity> items = fetchedItems.size() > pageSize
        ? fetchedItems.subList(0, pageSize)
        : fetchedItems;

    if (cursorInstId == null && nextKey == null) {
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

}
