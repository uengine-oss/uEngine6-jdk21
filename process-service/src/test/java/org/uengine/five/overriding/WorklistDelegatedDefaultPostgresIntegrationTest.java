package org.uengine.five.overriding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.repository.WorklistRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * PostgreSQL-only regression check for the WorklistEntity delegated default.
 */
@EnabledIfEnvironmentVariable(named = "LOAN_BPM_TEST_JDBC_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LOAN_BPM_TEST_ALLOW_WRITE", matches = "true")
class WorklistDelegatedDefaultPostgresIntegrationTest {

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private WorklistRepository worklistRepository;

    private void connect() {
        String jdbcUrl = requiredLocalTestJdbcUrl(System.getenv("LOAN_BPM_TEST_JDBC_URL"));
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl,
                environmentOrDefault("LOAN_BPM_TEST_JDBC_USER", "uengine"),
                environmentOrDefault("LOAN_BPM_TEST_JDBC_PASSWORD", "uengine"));
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan(WorklistEntity.class.getPackageName());
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        factoryBean.setJpaProperties(properties);
        factoryBean.afterPropertiesSet();

        entityManagerFactory = factoryBean.getObject();
        entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        worklistRepository = new JpaRepositoryFactory(entityManager).getRepository(WorklistRepository.class);
    }

    private void close() {
        if (entityManager != null && entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
        if (entityManager != null) {
            entityManager.close();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void newWorklistPersistsDelegatedAsFalse() {
        connect();
        try {
            assertEquals(Boolean.FALSE, new WorklistEntity().getDelegated());

            String marker = "T002D-JPA-" + UUID.randomUUID();
            Long taskId = null;
            try {
                WorklistEntity created = new WorklistEntity();
                created.setTitle(marker);

                taskId = worklistRepository.saveAndFlush(created).getTaskId();
                WorklistEntity reloaded = worklistRepository.findById(taskId).orElseThrow();
                assertEquals(Boolean.FALSE, reloaded.getDelegated());
            } finally {
                if (taskId != null) {
                    worklistRepository.deleteById(taskId);
                    worklistRepository.flush();
                    assertFalse(worklistRepository.existsById(taskId));
                }
                Long markerRows = entityManager.createQuery(
                        "select count(worklist) from WorklistEntity worklist where worklist.title = :marker", Long.class)
                        .setParameter("marker", marker)
                        .getSingleResult();
                assertEquals(0L, markerRows);
            }
        } finally {
            close();
        }
    }

    @Test
    void rejectsNonLocalPostgresUrlBeforeDataSourceCreation() {
        assertThrows(IllegalStateException.class,
                () -> requiredLocalTestJdbcUrl("jdbc:postgresql://localhost:15433/uengine"));
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredLocalTestJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalStateException("LOAN_BPM_TEST_JDBC_URL must be jdbc:postgresql://localhost:5432/uengine");
        }

        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LOAN_BPM_TEST_JDBC_URL must be a local PostgreSQL test URL", e);
        }

        boolean localHost = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || !localHost || uri.getPort() != 5432
                || !"/uengine".equals(uri.getPath()) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("LOAN_BPM_TEST_JDBC_URL must be jdbc:postgresql://localhost:5432/uengine");
        }
        return jdbcUrl;
    }
}

