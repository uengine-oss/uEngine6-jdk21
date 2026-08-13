package org.uengine.hwlife.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class BulkAssignSearchRepository {

  @PersistenceContext
  private EntityManager entityManager;

  public BulkAssignSearchRepository() {
  }

  BulkAssignSearchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public SearchResult search(BulkAssignSearchRequest request, String belnOrgnCode) {
    BulkAssignSearchRequest normalized = request == null ? new BulkAssignSearchRequest() : request;
    String organization = SearchPredicates.trimToNull(belnOrgnCode);
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

    SearchPredicates.addText(builder, predicates, instance.get("bswrClsfCode"), request.getBpmBswrClsfCode());
    SearchPredicates.addText(builder, predicates, instance.get("custId"), request.getCustId());
    SearchPredicates.addText(builder, predicates, instance.get("loanCntcNo"), request.getLoanCntcNo());
    SearchPredicates.addText(builder, predicates, instance.get("fncgSuptTrgtDvsnCode"), request.getFncgSuptTrgtDvsnCode());
    SearchPredicates.addText(builder, predicates, instance.get("loanSubjDvsnCode"), request.getLoanSubjDvsnCode());
    SearchPredicates.addText(builder, predicates, instance.get("fncgMneyUsagClsfCode"), request.getFncgMneyUsagClsfCode());
    SearchDateRanges.addInclusive(
        builder, predicates, worklist.get("startDate"), request.getStarDate(), request.getEndDate());
    SearchDateRanges.addInclusive(
        builder,
        predicates,
        instance.get("loanHopeDate"),
        request.getHopeStarDate(),
        request.getHopeEndDate());
    SearchPredicates.addText(builder, predicates, instance.get("initGroupCd"), request.getFncgWndwOrgnCode());
    SearchPredicates.addText(builder, predicates, worklist.get("title"), request.getUworNm());
    SearchPredicates.addRootDefId(builder, query, predicates, instance, request.getBswrDvsnVal());
    return predicates.toArray(Predicate[]::new);
  }

  public record SearchResult(List<WorklistEntity> items, int totalCount) {
    public SearchResult {
      items = List.copyOf(items);
    }
  }
}
