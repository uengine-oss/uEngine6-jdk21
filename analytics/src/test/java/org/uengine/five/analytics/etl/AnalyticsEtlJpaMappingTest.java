package org.uengine.five.analytics.etl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.uengine.five.analytics.etl.entity.AnalyticsTaskFact;
import org.uengine.five.analytics.etl.repository.AnalyticsTaskFactRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics-etl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
@ContextConfiguration(classes = AnalyticsEtlJpaMappingTest.TestApplication.class)
class AnalyticsEtlJpaMappingTest {

    private final AnalyticsTaskFactRepository taskRepository;

    @Autowired
    AnalyticsEtlJpaMappingTest(AnalyticsTaskFactRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Test
    void createsAnalyticsTablesAndUpsertsNaturalKey() {
        AnalyticsTaskFact initial = new AnalyticsTaskFact();
        initial.setTaskId(99L);
        initial.setStatus("RUNNING");
        taskRepository.saveAndFlush(initial);

        AnalyticsTaskFact updated = new AnalyticsTaskFact();
        updated.setTaskId(99L);
        updated.setStatus("COMPLETED");
        updated.setDurationSeconds(120L);
        taskRepository.saveAndFlush(updated);

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(taskRepository.findById(99L)).get()
                .extracting(AnalyticsTaskFact::getStatus, AnalyticsTaskFact::getDurationSeconds)
                .containsExactly("COMPLETED", 120L);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "org.uengine.five.analytics.etl.entity")
    @EnableJpaRepositories(basePackages = "org.uengine.five.analytics.etl.repository")
    static class TestApplication {
    }
}
