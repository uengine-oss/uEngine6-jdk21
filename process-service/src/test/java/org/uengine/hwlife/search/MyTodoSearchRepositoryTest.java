package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.MyTodoSearchRepository.SearchResult;
import org.uengine.hwlife.search.dto.MyTodoRequest;

import jakarta.persistence.EntityManager;

class MyTodoSearchRepositoryTest {

  private static final String USER_ID = "cursor-user";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static SessionFactory sessionFactory;

  @BeforeAll
  static void setUpDatabase() {
    sessionFactory = new Configuration()
        .addAnnotatedClass(ProcessInstanceEntity.class)
        .addAnnotatedClass(WorklistEntity.class)
        .addAnnotatedClass(RoleMappingEntity.class)
        .setProperty("hibernate.connection.url",
            "jdbc:h2:mem:my_todo_inst_cursor;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .setProperty("hibernate.show_sql", "false")
        .buildSessionFactory();

    try (Session session = sessionFactory.openSession()) {
      session.beginTransaction();
      persist(session, 1L, 101L, "LOAN-A", at(2026, 8, 1, 9, 0), day(2026, 8, 10));
      persist(session, 2L, 102L, "LOAN-A", at(2026, 8, 2, 23, 59), day(2026, 8, 11));
      persist(session, 3L, 103L, "LOAN-A", at(2026, 8, 3, 0, 0), day(2026, 8, 12));
      persist(session, 4L, 104L, "LOAN-A", at(2026, 8, 4, 9, 0), day(2026, 8, 13));
      persist(session, 5L, 105L, "LOAN-A", at(2026, 8, 5, 9, 0), day(2026, 8, 14));
      persist(session, 6L, 106L, "LOAN-B", at(2026, 8, 6, 9, 0), day(2026, 9, 1));
      persist(session, 7L, 107L, "LOAN-B", at(2026, 8, 7, 9, 0), day(2026, 9, 2));
      session.getTransaction().commit();
    }
  }

  @AfterAll
  static void closeDatabase() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @Test
  void returnsNextPageByTaskIdCursorAndKeepsTotalCount() {
    MyTodoRequest request = new MyTodoRequest();

    SearchResult first = search(request, null, 2);
    SearchResult second = search(request, Long.valueOf(first.nextKey()), 2);
    SearchResult last = search(request, 101L, 2);

    assertEquals(List.of(7L, 6L), instIds(first));
    assertEquals("105", first.nextKey());
    assertEquals(7, first.totalCount());
    assertEquals(List.of(5L, 4L), instIds(second));
    assertEquals("103", second.nextKey());
    assertEquals(7, second.totalCount());
    assertEquals(List.of(1L), instIds(last));
    assertEquals(null, last.nextKey());
    assertEquals(7, last.totalCount());
  }

  @Test
  void filtersFixedRequestPropertiesAgainstProcessInstance() {
    MyTodoRequest request = new MyTodoRequest();
    request.setLoanCntcNo("LOAN-A");

    SearchResult result = search(request, null, 10);

    assertEquals(List.of(5L, 4L, 3L, 2L, 1L), instIds(result));
    assertEquals(5, result.totalCount());
  }

  @Test
  void includesWholeEndDateForWorklistStartDate() {
    MyTodoRequest request = new MyTodoRequest();
    request.setStarDate(day(2026, 8, 1));
    request.setEndDate(day(2026, 8, 2));

    SearchResult result = search(request, null, 10);

    assertEquals(List.of(2L, 1L), instIds(result));
  }

  @Test
  void filtersLoanHopeDateByInclusiveCalendarDays() {
    MyTodoRequest request = new MyTodoRequest();
    request.setHopeStarDate(day(2026, 8, 10));
    request.setHopeEndDate(day(2026, 8, 11));

    SearchResult result = search(request, null, 10);

    assertEquals(List.of(2L, 1L), instIds(result));
  }

  @Test
  void usesEmnbAsOwnWorkCondition() {
    MyTodoRequest request = new MyTodoRequest();

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      WorklistEntity worklist = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 1L)
          .getSingleResult();
      worklist.setEndpoint("12323");
      entityManager.getTransaction().commit();

      SearchResult result = new MyTodoSearchRepository(entityManager)
          .search(request, null, 10, "12323", null);

      assertEquals(List.of(1L), instIds(result));

      entityManager.getTransaction().begin();
      worklist.setEndpoint(USER_ID);
      entityManager.getTransaction().commit();
    }
  }

  @Test
  void usesBelnOrgnCodeForClaimableGroupWork() {
    MyTodoRequest request = new MyTodoRequest();

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      WorklistEntity worklist = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 1L)
          .getSingleResult();
      worklist.setEndpoint(null);
      worklist.setDispatchOption(1);
      worklist.setScope("SCOPE-ACCESS");
      worklist.setGroupCd("GROUP-ACCESS");
      entityManager.getTransaction().commit();

      SearchResult result = new MyTodoSearchRepository(entityManager)
          .search(request, null, 10, "different-user", "GROUP-ACCESS");

      assertEquals(List.of(1L), instIds(result));

      entityManager.getTransaction().begin();
      worklist.setEndpoint(USER_ID);
      worklist.setDispatchOption(0);
      worklist.setScope(null);
      worklist.setGroupCd("GROUP");
      entityManager.getTransaction().commit();
    }
  }

  @Test
  void usesEmnbForClaimedDispatchWorkWithoutOrganizationCode() {
    MyTodoRequest request = new MyTodoRequest();

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      WorklistEntity worklist = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 1L)
          .getSingleResult();
      worklist.setEndpoint("12323");
      worklist.setDispatchOption(1);
      worklist.setGroupCd("GROUP-ACCESS");
      entityManager.getTransaction().commit();

      SearchResult result = new MyTodoSearchRepository(entityManager)
          .search(request, null, 10, "12323", null);

      assertEquals(List.of(1L), instIds(result));

      entityManager.getTransaction().begin();
      worklist.setEndpoint(USER_ID);
      worklist.setDispatchOption(0);
      worklist.setGroupCd("GROUP");
      entityManager.getTransaction().commit();
    }
  }

  @Test
  void combinesOwnAndClaimableWorkWhenEmnbAndBelnOrgnCodeAreBothProvided() {
    MyTodoRequest request = new MyTodoRequest();

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      entityManager.getTransaction().begin();
      WorklistEntity ownWork = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 1L)
          .getSingleResult();
      ownWork.setEndpoint("12323");
      ownWork.setDispatchOption(1);
      ownWork.setGroupCd("GROUP-ACCESS");

      WorklistEntity claimableWork = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 2L)
          .getSingleResult();
      claimableWork.setEndpoint(null);
      claimableWork.setDispatchOption(1);
      claimableWork.setGroupCd("GROUP-ACCESS");

      WorklistEntity otherClaimedWork = entityManager.createQuery(
              "select worklist from WorklistEntity worklist where worklist.instId = :instId",
              WorklistEntity.class)
          .setParameter("instId", 3L)
          .getSingleResult();
      otherClaimedWork.setEndpoint("other-user");
      otherClaimedWork.setDispatchOption(1);
      otherClaimedWork.setGroupCd("GROUP-ACCESS");
      entityManager.getTransaction().commit();

      SearchResult result = new MyTodoSearchRepository(entityManager)
          .search(request, null, 10, "12323", "GROUP-ACCESS");

      assertEquals(List.of(2L, 1L), instIds(result));

      entityManager.getTransaction().begin();
      ownWork.setEndpoint(USER_ID);
      ownWork.setDispatchOption(0);
      ownWork.setGroupCd("GROUP");
      claimableWork.setEndpoint(USER_ID);
      claimableWork.setDispatchOption(0);
      claimableWork.setGroupCd("GROUP");
      otherClaimedWork.setEndpoint(USER_ID);
      otherClaimedWork.setDispatchOption(0);
      otherClaimedWork.setGroupCd("GROUP");
      entityManager.getTransaction().commit();
    }
  }

  @Test
  void returnsEmptyWhenEmnbAndBelnOrgnCodeAreBlank() {
    MyTodoRequest request = new MyTodoRequest();

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      SearchResult result = new MyTodoSearchRepository(entityManager)
          .search(request, null, 10, null, null);

      assertEquals(List.of(), instIds(result));
      assertEquals(0, result.totalCount());
    }
  }

  private static SearchResult search(MyTodoRequest request, Long cursor, int size) {
    return search(request, cursor, size, USER_ID, null);
  }

  private static SearchResult search(
      MyTodoRequest request,
      Long cursor,
      int size,
      String emnb,
      String belnOrgnCode) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return new MyTodoSearchRepository(entityManager)
          .search(request, cursor, size, emnb, belnOrgnCode);
    }
  }

  private static List<Long> instIds(SearchResult result) {
    return result.items().stream().map(WorklistEntity::getInstId).toList();
  }

  private static void persist(
      Session session,
      long instanceId,
      long taskId,
      String loanCntcNo,
      Date workStartDate,
      Date loanHopeDate) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instanceId);
    instance.setLoanCntcNo(loanCntcNo);
    instance.setLoanHopeDate(loanHopeDate);
    instance.setStatus("Running");
    ProcessInstanceEntity managedInstance = session.merge(instance);

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(instanceId);
    worklist.setProcessInstance(managedInstance);
    worklist.setEndpoint(USER_ID);
    worklist.setStatus("NEW");
    worklist.setStartDate(workStartDate);
    worklist.setDispatchOption(0);
    worklist.setGroupCd("GROUP");
    session.merge(worklist);
  }

  private static Date day(int year, int month, int day) {
    return at(year, month, day, 0, 0);
  }

  private static Date at(int year, int month, int day, int hour, int minute) {
    return Date.from(
        LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(SEOUL)
            .toInstant());
  }
}
