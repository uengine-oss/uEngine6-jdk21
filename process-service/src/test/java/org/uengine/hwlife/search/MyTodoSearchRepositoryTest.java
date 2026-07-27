package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.hwlife.search.MyTodoSearchRepository.SearchResult;
import org.uengine.hwlife.search.dto.MyTodoRequest;

import jakarta.persistence.EntityManager;

class MyTodoSearchRepositoryTest {

  private static final String USER_ID = "cursor-user";

  private static SessionFactory sessionFactory;

  @BeforeAll
  static void setUpDatabase() {
    sessionFactory = new Configuration()
        .addAnnotatedClass(ProcessInstanceEntity.class)
        .addAnnotatedClass(WorklistEntity.class)
        .addAnnotatedClass(RoleMappingEntity.class)
        .setProperty("hibernate.connection.url",
            "jdbc:h2:mem:my_todo_cursor;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .setProperty("hibernate.show_sql", "false")
        .buildSessionFactory();

    try (Session session = sessionFactory.openSession()) {
      session.beginTransaction();
      persist(session, 1L, 101L, 300_000_000L, 100_000_000L);
      persist(session, 2L, 102L, 300_000_000L, 300_000_000L);
      persist(session, 3L, 103L, 200_000_000L, 300_000_000L);
      persist(session, 4L, 104L, 100_000_000L, 200_000_000L);
      persist(session, 5L, 105L, null, null);
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
  void sortsRequestedLoanHopeDateDescendingWithTaskIdTieBreaker() {
    MyTodoRequest request = new MyTodoRequest();
    request.setSortOrdrVal("loanHopeDate");

    SearchResult result = search(request, null, 10);

    assertEquals(List.of(3L, 2L, 4L, 1L, 5L), taskIds(result));
  }

  @Test
  void defaultsToStartedDateDescendingForUnknownProperty() {
    MyTodoRequest request = new MyTodoRequest();
    request.setSortOrdrVal("DESC");

    SearchResult result = search(request, null, 10);

    assertEquals(List.of(2L, 1L, 3L, 4L, 5L), taskIds(result));
  }

  @Test
  void continuesAfterCursorWithoutDuplicatesWhenSortValuesAreEqual() {
    MyTodoRequest request = new MyTodoRequest();
    request.setSortOrdrVal("startedDate");

    SearchResult first = search(request, null, 2);
    SearchResult second = search(request, 1L, 2);
    SearchResult third = search(request, 4L, 2);

    assertEquals(List.of(2L, 1L), taskIds(first));
    assertEquals(List.of(3L, 4L), taskIds(second));
    assertEquals(List.of(5L), taskIds(third));
    assertEquals(5, first.totalCount());
    assertEquals(5, second.totalCount());
    assertEquals(5, third.totalCount());
  }

  private static SearchResult search(MyTodoRequest request, Long cursor, int size) {
    UserContext userContext = UserContext.getThreadLocalInstance();
    userContext.setUserId(USER_ID);
    userContext.setGroups(List.of());
    userContext.setScopes(List.of());

    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return new MyTodoSearchRepository(entityManager)
          .search(request, cursor, size, userContext);
    }
  }

  private static List<Long> taskIds(SearchResult result) {
    return result.items().stream().map(WorklistEntity::getTaskId).toList();
  }

  private static void persist(
      Session session,
      long instanceId,
      long taskId,
      Long startedDate,
      Long loanHopeDate) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instanceId);
    instance.setStartedDate(date(startedDate));
    instance.setLaonHopeDate(date(loanHopeDate));
    instance.setStatus("Running");
    ProcessInstanceEntity managedInstance = session.merge(instance);

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(instanceId);
    worklist.setProcessInstance(managedInstance);
    worklist.setEndpoint(USER_ID);
    worklist.setStatus("NEW");
    worklist.setStartDate(new Date(taskId));
    worklist.setDispatchOption(0);
    session.merge(worklist);
  }

  private static Date date(Long value) {
    return value == null ? null : new Date(value);
  }
}
