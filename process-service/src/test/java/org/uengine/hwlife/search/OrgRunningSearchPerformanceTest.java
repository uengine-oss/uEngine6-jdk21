package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.dto.OrgRunningRequest;

import jakarta.persistence.EntityManager;

class OrgRunningSearchPerformanceTest {

  private static final int ROW_COUNT = 100_000;
  private static final int PAGE_SIZE = 20;
  private static final int WARM_UPS = 2;
  private static final int SAMPLES = 10;

  private static SessionFactory sessionFactory;

  @BeforeAll
  static void setUpDatabase() {
    sessionFactory = new Configuration()
        .addAnnotatedClass(ProcessInstanceEntity.class)
        .addAnnotatedClass(WorklistEntity.class)
        .addAnnotatedClass(RoleMappingEntity.class)
        .setProperty(
            "hibernate.connection.url",
            "jdbc:h2:mem:org_running_perf;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .setProperty("hibernate.show_sql", "false")
        .setProperty("hibernate.generate_statistics", "true")
        .buildSessionFactory();
    seed();
  }

  @AfterAll
  static void closeDatabase() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @Test
  void comparesPreviousFullLoadWithDatabaseCursorSearch() {
    for (int index = 0; index < WARM_UPS; index++) {
      previousSearch();
      optimizedSearch();
    }

    Measurement before = measure(this::previousSearch);
    Measurement after = measure(this::optimizedSearch);

    assertEquals(before.lastResult().totalCount(), after.lastResult().totalCount());
    assertEquals(before.lastResult().taskIds(), after.lastResult().taskIds());
    assertEquals(ROW_COUNT, after.lastResult().totalCount());
    assertEquals(PAGE_SIZE, after.lastResult().taskIds().size());
    assertEquals(1, before.statementsPerRequest());
    assertEquals(2, after.statementsPerRequest());
    assertTrue(after.p95Ms() < before.p95Ms());

    System.out.printf(
        Locale.ROOT,
        "ORG_RUNNING_PERF|rows=%d|samples=%d"
            + "|beforeMinMs=%d|beforeMedianMs=%d|beforeAvgMs=%.1f"
            + "|beforeP95Ms=%d|beforeMaxMs=%d|beforeSql=%d|beforeLoadedRows=%d"
            + "|afterMinMs=%d|afterMedianMs=%d|afterAvgMs=%.1f"
            + "|afterP95Ms=%d|afterMaxMs=%d|afterSql=%d|afterLoadedRows=%d%n",
        ROW_COUNT,
        SAMPLES,
        before.minMs(),
        before.medianMs(),
        before.averageMs(),
        before.p95Ms(),
        before.maxMs(),
        before.statementsPerRequest(),
        before.lastResult().loadedRows(),
        after.minMs(),
        after.medianMs(),
        after.averageMs(),
        after.p95Ms(),
        after.maxMs(),
        after.statementsPerRequest(),
        after.lastResult().loadedRows());
  }

  private Measurement measure(java.util.function.Supplier<SearchResult> operation) {
    Statistics statistics = sessionFactory.getStatistics();
    statistics.clear();
    List<Long> times = new ArrayList<>();
    SearchResult result = null;
    for (int index = 0; index < SAMPLES; index++) {
      long startedAt = System.nanoTime();
      result = operation.get();
      times.add((System.nanoTime() - startedAt) / 1_000_000);
    }
    times.sort(Comparator.naturalOrder());
    double average = times.stream().mapToLong(Long::longValue).average().orElse(0);
    return new Measurement(
        times.get(0),
        times.get((times.size() - 1) / 2),
        average,
        percentile(times, 0.95),
        times.get(times.size() - 1),
        statistics.getPrepareStatementCount() / SAMPLES,
        result);
  }

  private SearchResult previousSearch() {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      List<WorklistEntity> all = entityManager.createQuery(
          "select wl from WorklistEntity wl join fetch wl.processInstance "
              + "where upper(wl.status) in ('NEW', 'RUNNING')",
          WorklistEntity.class)
          .getResultList();
      List<Long> taskIds = all.stream()
          .sorted(Comparator
              .comparing(
                  (WorklistEntity item) -> item.getProcessInstance().getStartedDate(),
                  Comparator.nullsLast(Comparator.reverseOrder()))
              .thenComparing(WorklistEntity::getTaskId, Comparator.reverseOrder()))
          .limit(PAGE_SIZE)
          .map(WorklistEntity::getTaskId)
          .toList();
      return new SearchResult(taskIds, all.size(), all.size());
    }
  }

  private SearchResult optimizedSearch() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setPageSize(PAGE_SIZE);
    request.setSortOrdrVal("startedDate");
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      OrgRunningSearchRepository.SearchResult result =
          new OrgRunningSearchRepository(entityManager).search(request, null, PAGE_SIZE);
      return new SearchResult(
          result.items().stream().map(WorklistEntity::getTaskId).toList(),
          result.totalCount(),
          result.items().size());
    }
  }

  private static void seed() {
    try (Session session = sessionFactory.openSession()) {
      session.doWork(connection -> {
        try (Statement statement = connection.createStatement()) {
          statement.execute("""
              insert into BPM_PROCINST (
                instId, startedDate, loanHopeDate, status, initComCd,
                bswrClsfCode, fncgBswrDvsnCode, custId, corrKey,
                deleted, adhoc, subProcess, eventHandler, archive, dontReturn
              )
              select x, dateadd('SECOND', mod(x, 50000), timestamp '2026-01-01 00:00:00'),
                     dateadd('DAY', mod(x, 365), date '2026-01-01'),
                     'Running', 'REQ-' || mod(x, 20), 'BSWR-' || mod(x, 5),
                     'LOAN', 'CUST-' || x, 'ORG-RUN-' || x,
                     false, false, false, false, false, false
              from system_range(1, 100000)
              """);
          statement.execute("""
              insert into BPM_WORKLIST (
                taskId, instId, title, endpoint, scope, assignGroup,
                trcTag, startDate, status, assignType, dispatchOption, rootInstId
              )
              select x, x, 'Organization work ' || x, 'handler-' || mod(x, 100),
                     'SCOPE-' || mod(x, 20), 'PROC-' || mod(x, 20),
                     'TRACE-' || mod(x, 10),
                     dateadd('SECOND', mod(x, 50000), timestamp '2026-01-01 00:00:00'),
                     case when mod(x, 2) = 0 then 'NEW' else 'RUNNING' end,
                     0, 0, x
              from system_range(1, 100000)
              """);
        }
      });
    }
  }

  private static long percentile(List<Long> sorted, double percentile) {
    int index = (int) Math.ceil(sorted.size() * percentile) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private record SearchResult(List<Long> taskIds, int totalCount, int loadedRows) {
  }

  private record Measurement(
      long minMs,
      long medianMs,
      double averageMs,
      long p95Ms,
      long maxMs,
      long statementsPerRequest,
      SearchResult lastResult) {
  }
}
