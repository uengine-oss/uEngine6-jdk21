package org.uengine.hwlife.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
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
import org.uengine.hwlife.search.OrgRunningSearchRepository.SearchResult;
import org.uengine.hwlife.search.dto.OrgRunningRequest;

import jakarta.persistence.EntityManager;

class OrgRunningSearchRepositoryTest {

  private static final LocalDateTime JAN_10 = LocalDateTime.of(2026, 1, 10, 0, 0);
  private static final LocalDateTime JAN_11 = LocalDateTime.of(2026, 1, 11, 0, 0);
  private static final LocalDateTime JAN_12 = LocalDateTime.of(2026, 1, 12, 0, 0);
  private static final LocalDateTime JAN_13 = LocalDateTime.of(2026, 1, 13, 0, 0);
  private static final LocalDateTime JAN_14 = LocalDateTime.of(2026, 1, 14, 0, 0);

  private static SessionFactory sessionFactory;

  @BeforeAll
  static void setUpDatabase() {
    sessionFactory = new Configuration()
        .addAnnotatedClass(ProcessInstanceEntity.class)
        .addAnnotatedClass(WorklistEntity.class)
        .addAnnotatedClass(RoleMappingEntity.class)
        .setProperty(
            "hibernate.connection.url",
            "jdbc:h2:mem:org_running_cursor;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .setProperty("hibernate.show_sql", "false")
        .buildSessionFactory();

    try (Session session = sessionFactory.openSession()) {
      session.beginTransaction();
      persist(session, 1L, 101L, JAN_14, JAN_10, "NEW", "REQ-A", "PROC-A", "SCOPE-A");
      persist(session, 2L, 102L, JAN_14, JAN_11, "RUNNING", "REQ-A", "PROC-A", "SCOPE-A");
      persist(session, 3L, 103L, JAN_13, JAN_12, "NEW", "REQ-B", "PROC-B", "SCOPE-B");
      persist(session, 4L, 104L, JAN_12, JAN_13, "NEW", "REQ-B", null, "PROC-A");
      persist(session, 5L, 105L, JAN_11, JAN_14, "COMPLETED", "REQ-A", "PROC-A", "SCOPE-A");
      persist(session, 6L, 106L, null, null, "NEW", "REQ-A", "PROC-A", "SCOPE-A");
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
  void appliesEveryPopulatedFilterWithInclusiveRequestDateBounds() {
    OrgRunningRequest request = fullRequest();
    request.setRqstStarDttm(JAN_10);
    request.setRqstEndDttm(JAN_10);

    SearchResult result = search(request, null, 20);

    assertEquals(List.of(1L), taskIds(result));
    assertEquals(1, result.totalCount());
  }

  @Test
  void switchesBetweenProcessingAndRequestOrganization() {
    OrgRunningRequest processingRequest = new OrgRunningRequest();
    processingRequest.setFncgWndwOrgnCode("PROC-A");
    processingRequest.setSortOrdrVal("taskId");
    OrgRunningRequest requestOrganization = new OrgRunningRequest();
    requestOrganization.setFncgWndwOrgnCode("REQ-B");
    requestOrganization.setRqstDvsnCode("Y");
    requestOrganization.setSortOrdrVal("taskId");

    SearchResult processing = search(processingRequest, null, 20);
    SearchResult requested = search(requestOrganization, null, 20);

    assertEquals(List.of(6L, 4L, 2L, 1L), taskIds(processing));
    assertEquals(List.of(4L, 3L), taskIds(requested));
  }

  @Test
  void excludesCompletedWorkAndContinuesDescendingWithoutDuplicates() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setSortOrdrVal("startedDate");
    request.setSortDirection("DESC");

    SearchResult first = search(request, null, 2);
    SearchResult second = search(request, 1L, 2);
    SearchResult third = search(request, 4L, 2);

    assertEquals(List.of(2L, 1L), taskIds(first));
    assertEquals(List.of(3L, 4L), taskIds(second));
    assertEquals(List.of(6L), taskIds(third));
    assertEquals(5, first.totalCount());
    assertEquals(5, second.totalCount());
    assertEquals(5, third.totalCount());
  }

  @Test
  void continuesAscendingWithoutDuplicatesAndKeepsNullDatesLast() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setSortOrdrVal("loanHopeDate");
    request.setSortDirection("ASC");

    SearchResult first = search(request, null, 2);
    SearchResult second = search(request, 2L, 2);
    SearchResult third = search(request, 4L, 2);

    assertEquals(List.of(1L, 2L), taskIds(first));
    assertEquals(List.of(3L, 4L), taskIds(second));
    assertEquals(List.of(6L), taskIds(third));
  }

  @Test
  void returnsEmptyResultForUnknownCursor() {
    SearchResult result = search(new OrgRunningRequest(), 999L, 20);

    assertEquals(List.of(), taskIds(result));
    assertEquals(0, result.totalCount());
  }

  private static OrgRunningRequest fullRequest() {
    OrgRunningRequest request = new OrgRunningRequest();
    request.setBswrClsfCode("BSWR");
    request.setFncgVswrDvsnCode("LOAN");
    request.setFncgBpmTaskTrcgNm("TRACE");
    request.setFncgSuptTrgtDvsnCode("TARGET");
    request.setLoanSubjDvsnCode("SUBJECT");
    request.setFncgMneyUsagClsfCode("USAGE");
    request.setLoanCntcNo("CONTACT");
    request.setCustId("CUST");
    request.setFncgWndwOrgnCode("PROC-A");
    request.setSortOrdrVal("taskId");
    return request;
  }

  private static SearchResult search(OrgRunningRequest request, Long cursor, int size) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return new OrgRunningSearchRepository(entityManager).search(request, cursor, size);
    }
  }

  private static List<Long> taskIds(SearchResult result) {
    return result.items().stream().map(WorklistEntity::getTaskId).toList();
  }

  private static void persist(
      Session session,
      long instanceId,
      long taskId,
      LocalDateTime startedDate,
      LocalDateTime workStartedDate,
      String status,
      String requestOrganization,
      String processingOrganization,
      String scope) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instanceId);
    instance.setStartedDate(date(startedDate));
    instance.setLoanHopeDate(date(workStartedDate));
    instance.setStatus("Running");
    instance.setInitComCd(requestOrganization);
    instance.setInitEp("reporter");
    instance.setBswrClsfCode("BSWR");
    instance.setFncgBswrDvsnCode("LOAN");
    instance.setFncgSuptTrgtDvsnCode("TARGET");
    instance.setLoanSubjDvsnCode("SUBJECT");
    instance.setFncgMneyUsagClsfCode("USAGE");
    instance.setLoanCntcNo("CONTACT");
    instance.setCustId("CUST");
    instance.setCorrKey("CORR-" + instanceId);
    ProcessInstanceEntity managedInstance = session.merge(instance);

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(instanceId);
    worklist.setProcessInstance(managedInstance);
    worklist.setStatus(status);
    worklist.setStartDate(date(workStartedDate));
    worklist.setAssignGroup(processingOrganization);
    worklist.setScope(scope);
    worklist.setTrcTag("TRACE");
    worklist.setEndpoint("handler");
    worklist.setTitle("Unit work " + taskId);
    session.merge(worklist);
  }

  private static Date date(LocalDateTime value) {
    return value == null
        ? null
        : Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
  }
}
