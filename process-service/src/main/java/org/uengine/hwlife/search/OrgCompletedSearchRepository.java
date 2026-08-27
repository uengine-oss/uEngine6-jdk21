package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.hwlife.search.dto.OrgCompletedRequest;
import org.uengine.kernel.Activity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public class OrgCompletedSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public OrgCompletedSearchRepository() {
  }

  OrgCompletedSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public SearchResult search(
      OrgCompletedRequest request,
      Long cursorId,
      int pageSize,
      String belnOrgnCode) {
    String organizationCode = organizationCode(request, belnOrgnCode);
    if (organizationCode == null) {
      return new SearchResult(List.of(), 0, null);
    }

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    String sortKey = sortKey(request);
    CursorPosition cursor = findCursor(builder, cursorId, sortKey);
    if (cursorId != null && cursor == null) {
      return new SearchResult(List.of(), 0, null);
    }

    CriteriaQuery<ProcessInstanceEntity> dataQuery = builder.createQuery(ProcessInstanceEntity.class);
    Root<ProcessInstanceEntity> instance = dataQuery.from(ProcessInstanceEntity.class);
    List<Predicate> dataPredicates = new ArrayList<>(
        List.of(predicates(builder, dataQuery, instance, request, organizationCode)));
    // nextKey(instId)는 정렬·비즈니스 필터가 아니라, 정렬된 결과의 페이지 커서다.
    if (cursor != null) {
      dataPredicates.add(cursorPredicate(builder, instance, sortKey, cursor));
    }
    dataQuery.select(instance)
        .where(dataPredicates.toArray(Predicate[]::new))
        .orderBy(sortOrders(builder, instance, sortKey));

    TypedQuery<ProcessInstanceEntity> query = entityManager.createQuery(dataQuery);
    query.setMaxResults(pageSize + 1);
    List<ProcessInstanceEntity> fetchedItems = query.getResultList();
    String nextKey = fetchedItems.size() > pageSize
        ? String.valueOf(fetchedItems.get(pageSize).getInstId())
        : null;
    List<ProcessInstanceEntity> items = fetchedItems.size() > pageSize
        ? fetchedItems.subList(0, pageSize)
        : fetchedItems;

    if (cursor == null && nextKey == null) {
      int totalCount = items.size();
      return new SearchResult(items, totalCount, null);
    }

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<ProcessInstanceEntity> countInstance = countQuery.from(ProcessInstanceEntity.class);
    countQuery.select(builder.count(countInstance))
        .where(predicates(builder, countQuery, countInstance, request, organizationCode));
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
    if (isInstIdSort(sortKey)) {
      return new CursorPosition(null, cursorId);
    }

    CriteriaQuery<Tuple> cursorQuery = builder.createTupleQuery();
    Root<ProcessInstanceEntity> instance = cursorQuery.from(ProcessInstanceEntity.class);
    cursorQuery.multiselect(
            instance.get("instId").alias("instId"),
            dateSortPath(instance, sortKey).alias("sortValue"))
        .where(builder.equal(instance.get("instId"), cursorId));
    List<Tuple> rows = entityManager.createQuery(cursorQuery)
        .setMaxResults(1)
        .getResultList();
    return rows.isEmpty()
        ? null
        : new CursorPosition(rows.get(0).get("sortValue", Date.class), cursorId);
  }

  private static Predicate[] predicates(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      Root<ProcessInstanceEntity> instance,
      OrgCompletedRequest request,
      String organizationCode) {
    List<Predicate> predicates = new ArrayList<>();
    // 1) 인스턴스 종료(Completed)
    predicates.add(builder.equal(instance.get("status"), Activity.STATUS_COMPLETED));
    // 2) 요청기관: fncgWndwOrgnCode 우선, 없으면 ESB header.belnOrgnCode → init_group_cd
    predicates.add(builder.equal(instance.get("initGroupCd"), organizationCode));

    addRootDefId(builder, query, predicates, instance, request.getBswrDvsnVal());
    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addDateRange(
        builder,
        predicates,
        instance.get("startedDate"),
        request.getRqstStarDate(),
        request.getRqstEndDate());
    return predicates.toArray(Predicate[]::new);
  }

  /**
   * 서브프로세스도 루트 프로세스 정의로 필터링한다.
   * {@code coalesce(instance.rootInstId, instance.instId)} 의 {@code defId == bswrDvsnVal}.
   */
  private static void addRootDefId(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      List<Predicate> predicates,
      Root<ProcessInstanceEntity> instance,
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
      Root<ProcessInstanceEntity> instance,
      String sortKey,
      CursorPosition cursor) {
    Path<Long> instId = instance.get("instId");
    if (isInstIdSort(sortKey)) {
      return compareInclusive(builder, instId, cursor.instId());
    }
    Path<Date> sortPath = dateSortPath(instance, sortKey);
    if (cursor.sortValue() == null) {
      return builder.and(
          builder.isNull(sortPath),
          compareInclusive(builder, instId, cursor.instId()));
    }
    return builder.or(
        builder.isNull(sortPath),
        compare(builder, sortPath, cursor.sortValue()),
        builder.and(
            builder.equal(sortPath, cursor.sortValue()),
            compareInclusive(builder, instId, cursor.instId())));
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
      Root<ProcessInstanceEntity> instance,
      String sortKey) {
    if (isInstIdSort(sortKey)) {
      return List.of(builder.desc(instance.get("instId")));
    }
    Path<Date> sortPath = dateSortPath(instance, sortKey);
    return List.of(
        builder.asc(builder.selectCase().when(builder.isNull(sortPath), 1).otherwise(0)),
        builder.desc(sortPath),
        builder.desc(instance.get("instId")));
  }

  private Path<Date> dateSortPath(Root<ProcessInstanceEntity> instance, String sortKey) {
    if (isInstIdSort(sortKey)) {
      throw new IllegalArgumentException("instId is not a date sort field");
    }
    if (hasAttribute(ProcessInstanceEntity.class, sortKey)) {
      return instance.get(sortKey);
    }
    throw new IllegalArgumentException("Unsupported sortOrdrVal: " + sortKey);
  }

  /**
   * {@code sortOrdrVal} 이 인스턴스 속성명이면 그대로 사용하고, 없거나 미지원이면 {@code instId}.
   */
  private String sortKey(OrgCompletedRequest request) {
    String value = trimToNull(request.getSortOrdrVal());
    if (value == null || isInstIdSort(value) || "taskId".equals(value)) {
      return "instId";
    }
    if (hasAttribute(ProcessInstanceEntity.class, value)) {
      return value;
    }
    return "instId";
  }

  private boolean hasAttribute(Class<?> entityType, String attributeName) {
    try {
      entityManager.getMetamodel().entity(entityType).getAttribute(attributeName);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static boolean isInstIdSort(String sortKey) {
    return "instId".equals(trimToNull(sortKey));
  }

  /**
   * 요청기관: {@code fncgWndwOrgnCode} 가 있으면 그 값, 없으면 ESB header {@code belnOrgnCode}.
   */
  private static String organizationCode(OrgCompletedRequest request, String belnOrgnCode) {
    String requested = request == null ? null : trimToNull(request.getFncgWndwOrgnCode());
    if (requested != null) {
      return requested;
    }
    return trimToNull(belnOrgnCode);
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

  public record SearchResult(List<ProcessInstanceEntity> items, int totalCount, String nextKey) {

    public SearchResult {
      items = List.copyOf(items);
    }

    public SearchResult(List<ProcessInstanceEntity> items, int totalCount) {
      this(items, totalCount, null);
    }
  }

  private record CursorPosition(Date sortValue, Long instId) {
  }

}
