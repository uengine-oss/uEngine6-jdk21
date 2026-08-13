package org.uengine.hwlife.search;

import java.util.List;

import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * 검색 Criteria 공통 조건 조각.
 *
 * <p>조회별 where 조합은 각 Repository에 두고, 동일하게 반복되는 equal / 루트 정의 필터만 모은다.</p>
 */
final class SearchPredicates {

  private SearchPredicates() {
  }

  static void addText(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<String> path,
      String expected) {
    String value = trimToNull(expected);
    if (value != null) {
      predicates.add(builder.equal(path, value));
    }
  }

  /**
   * 서브프로세스 단위업무도 루트 프로세스 정의로 필터링한다.
   * {@code coalesce(instance.rootInstId, instance.instId)} 의 {@code defId} 일치.
   */
  static void addRootDefId(
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
    Expression<Long> rootInstId =
        builder.coalesce(instance.get("rootInstId"), instance.get("instId"));
    rootMatch.select(rootInstance.get("instId"))
        .where(
            builder.equal(rootInstance.get("instId"), rootInstId),
            builder.equal(rootInstance.get("defId"), value));
    predicates.add(builder.exists(rootMatch));
  }

  static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
