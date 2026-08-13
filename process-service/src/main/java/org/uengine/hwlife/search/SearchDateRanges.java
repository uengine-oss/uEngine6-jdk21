package org.uengine.hwlife.search;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * 검색 요청 일자({@code yyyyMMdd})를 시작일·종료일 모두 포함하도록 적용한다.
 *
 * <p>컬럼에 시각이 있으면 {@code path <= 종료일 00:00:00} 은 당일 건을 빠뜨린다.
 * 반열린 구간 {@code start <= path < end+1day} 를 쓴다.</p>
 */
final class SearchDateRanges {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private SearchDateRanges() {
  }

  /**
   * 시작일 0시 이상, 종료일 다음날 0시 미만.
   * {@code null} 인 쪽은 조건을 넣지 않는다.
   */
  static void addInclusive(
      CriteriaBuilder builder,
      List<Predicate> predicates,
      Path<Date> path,
      Date startInclusive,
      Date endInclusive) {
    if (startInclusive != null) {
      predicates.add(builder.greaterThanOrEqualTo(path, toSqlDate(toLocalDate(startInclusive))));
    }
    if (endInclusive != null) {
      predicates.add(builder.lessThan(path, toSqlDate(toLocalDate(endInclusive).plusDays(1))));
    }
  }

  private static LocalDate toLocalDate(Date value) {
    Instant instant = Instant.ofEpochMilli(value.getTime());
    return instant.atZone(ZONE).toLocalDate();
  }

  private static java.sql.Date toSqlDate(LocalDate localDate) {
    return java.sql.Date.valueOf(localDate);
  }
}
