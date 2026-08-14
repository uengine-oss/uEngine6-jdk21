package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public class BulkAssignSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public BulkAssignSearchRepository() {
  }

  BulkAssignSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * 일괄배정 대상 조회 — {@code dispatchOption == 1}, 미배정, 동일 기관({@code groupCd == belnOrgnCode})만.
   *
   * @param belnOrgnCode ESB header 소속기관코드
   */
  public SearchResult search(BulkAssignSearchRequest request, String belnOrgnCode) {
    BulkAssignSearchRequest normalized = request == null ? new BulkAssignSearchRequest() : request;
    String organization = trimToNull(belnOrgnCode);
    if (organization == null) {
      return new SearchResult(List.of(), 0);
    }
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();

    CriteriaQuery<WorklistEntity> dataQuery = builder.createQuery(WorklistEntity.class);
    Root<WorklistEntity> worklist = dataQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> instance =
        worklist.join("processInstance", JoinType.LEFT);
    dataQuery.select(worklist)
        .where(predicates(builder, dataQuery, worklist, instance, normalized, organization))
        .orderBy(builder.desc(worklist.get("taskId")));
    List<WorklistEntity> items = entityManager.createQuery(dataQuery).getResultList();

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<WorklistEntity> countWorklist = countQuery.from(WorklistEntity.class);
    Join<WorklistEntity, ProcessInstanceEntity> countInstance =
        countWorklist.join("processInstance", JoinType.LEFT);
    countQuery.select(builder.count(countWorklist))
        .where(predicates(builder, countQuery, countWorklist, countInstance, normalized, organization));
    long totalCount = entityManager.createQuery(countQuery).getSingleResult();
    return new SearchResult(items, Math.toIntExact(totalCount));
  }

  private static Predicate[] predicates(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      Root<WorklistEntity> worklist,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      BulkAssignSearchRequest request,
      String belnOrgnCode) {
    List<Predicate> predicates = new ArrayList<>();
    Path<String> endpoint = worklist.get("endpoint");
    predicates.add(builder.equal(builder.upper(worklist.get("status")), "NEW"));
    predicates.add(builder.equal(worklist.get("dispatchOption"), 1));
    predicates.add(builder.or(builder.isNull(endpoint), builder.equal(builder.trim(endpoint), "")));
    predicates.add(builder.equal(builder.trim(worklist.get("groupCd")), belnOrgnCode));

    addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    addText(builder, predicates, instance.get("custId"), request.getCustId());
    addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    addDateRange(builder, predicates, worklist.get("startDate"), request.getStarDate(), request.getEndDate());
    addDateRange(builder, predicates, instance.get("loanHopeDate"), request.getHopeStarDate(), request.getHopeEndDate());
    addText(builder, predicates, instance.get("initGroupCd"), request.getFncgWndwOrgnCode());
    addText(builder, predicates, worklist.get("title"), request.getUworNm());
    addRootDefId(builder, query, predicates, instance, request.getBswrDvsnVal());
    return predicates.toArray(Predicate[]::new);
  }

  private static void addRootDefId(
      CriteriaBuilder builder,
      AbstractQuery<?> query,
      List<Predicate> predicates,
      Join<WorklistEntity, ProcessInstanceEntity> instance,
      String expectedDefId) {
    String value = trimToNull(expectedDefId);
    if (value == null) {
      return;
    }
    Subquery<Long> rootMatch = query.subquery(Long.class);
    Root<ProcessInstanceEntity> rootInstance = rootMatch.from(ProcessInstanceEntity.class);
    Expression<Long> rootInstId = builder.coalesce(instance.get("rootInstId"), instance.get("instId"));
    rootMatch.select(rootInstance.get("instId"))
        .where(
            builder.equal(rootInstance.get("instId"), rootInstId),
            builder.equal(rootInstance.get("defId"), value));
    predicates.add(builder.exists(rootMatch));
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

  public record SearchResult(List<WorklistEntity> items, int totalCount) {
    public SearchResult {
      items = List.copyOf(items);
    }
  }
}
