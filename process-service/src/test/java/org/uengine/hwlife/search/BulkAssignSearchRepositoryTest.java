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
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;

import jakarta.persistence.EntityManager;

class BulkAssignSearchRepositoryTest {

  private static final LocalDateTime TEST_DATE = LocalDateTime.of(2026, 8, 12, 10, 0);
  private static SessionFactory sessionFactory;

  @BeforeAll
  static void setUpDatabase() {
    sessionFactory = new Configuration()
        .addAnnotatedClass(ProcessInstanceEntity.class)
        .addAnnotatedClass(WorklistEntity.class)
        .addAnnotatedClass(RoleMappingEntity.class)
        .setProperty("hibernate.connection.url", "jdbc:h2:mem:bulk_assign_search;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto", "create-drop")
        .setProperty("hibernate.show_sql", "false")
        .buildSessionFactory();

    try (Session session = sessionFactory.openSession()) {
      session.beginTransaction();
      persist(session, 1L, 101L, "NEW", 1, null, "OTHER");
      persist(session, 2L, 102L, "NEW", 0, null, "CUST");
      persist(session, 3L, 103L, "COMPLETED", 1, null, "CUST");
      persist(session, 4L, 104L, "NEW", 1, "hong", "CUST");
      persist(session, 5L, 105L, "NEW", 1, "", "CUST");
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
  void returnsOnlyNewClaimableUnassignedWorkitems() {
    BulkAssignSearchRepository.SearchResult result = search(new BulkAssignSearchRequest());

    assertEquals(List.of(5L, 1L), taskIds(result));
    assertEquals(2, result.totalCount());
  }

  @Test
  void appliesEveryExistingRequestFilter() {
    BulkAssignSearchRequest request = new BulkAssignSearchRequest();
    request.setBpmBswrClsfCode("BSWR");
    request.setCustId("CUST");
    request.setLoanCntcNo("CONTACT");
    request.setFncgSuptTrgtDvsnCode("TARGET");
    request.setLoanSubjDvsnCode("SUBJECT");
    request.setFncgMneyUsagClsfCode("USAGE");
    request.setStarDate(date(TEST_DATE));
    request.setEndDate(date(TEST_DATE));
    request.setHopeStarDate(date(TEST_DATE));
    request.setHopeEndDate(date(TEST_DATE));
    request.setFncgWndwOrgnCode("REQUEST-GROUP");
    request.setBswrDvsnVal("ROOT-DEF");
    request.setUworNm("Unit work 105");

    BulkAssignSearchRepository.SearchResult result = search(request);

    assertEquals(List.of(5L), taskIds(result));
    assertEquals(1, result.totalCount());
  }

  private static BulkAssignSearchRepository.SearchResult search(BulkAssignSearchRequest request) {
    try (EntityManager entityManager = sessionFactory.createEntityManager()) {
      return new BulkAssignSearchRepository(entityManager).search(request);
    }
  }

  private static List<Long> taskIds(BulkAssignSearchRepository.SearchResult result) {
    return result.items().stream().map(WorklistEntity::getTaskId).toList();
  }

  private static void persist(
      Session session,
      long instanceId,
      long taskId,
      String status,
      int dispatchOption,
      String endpoint,
      String customerId) {
    ProcessInstanceEntity instance = new ProcessInstanceEntity();
    instance.setInstId(instanceId);
    instance.setDefId("ROOT-DEF");
    instance.setStartedDate(date(TEST_DATE));
    instance.setLoanHopeDate(date(TEST_DATE));
    instance.setStatus("Running");
    instance.setInitGroupCd("REQUEST-GROUP");
    instance.setBswrClsfCode("BSWR");
    instance.setFncgSuptTrgtDvsnCode("TARGET");
    instance.setLoanSubjDvsnCode("SUBJECT");
    instance.setFncgMneyUsagClsfCode("USAGE");
    instance.setLoanCntcNo("CONTACT");
    instance.setCustId(customerId);
    instance.setCorrKey("CORR-" + instanceId);
    ProcessInstanceEntity managedInstance = session.merge(instance);

    WorklistEntity worklist = new WorklistEntity();
    worklist.setTaskId(taskId);
    worklist.setInstId(instanceId);
    worklist.setProcessInstance(managedInstance);
    worklist.setStatus(status);
    worklist.setDispatchOption(dispatchOption);
    worklist.setEndpoint(endpoint);
    worklist.setStartDate(date(TEST_DATE));
    worklist.setTitle("Unit work " + taskId);
    session.merge(worklist);
  }

  private static Date date(LocalDateTime value) {
    return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
  }
}
