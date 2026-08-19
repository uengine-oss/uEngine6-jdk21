package org.uengine.five.analytics.etl;

import org.junit.jupiter.api.Test;
import org.uengine.five.analytics.etl.entity.AnalyticsDateDimension;
import org.uengine.five.analytics.etl.entity.AnalyticsProcessInstanceFact;
import org.uengine.five.analytics.etl.entity.AnalyticsTaskFact;
import org.uengine.five.analytics.source.AnalyticsProcessInstanceSource;
import org.uengine.five.analytics.source.AnalyticsTaskSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsEtlTransformerTest {

    private final AnalyticsEtlTransformer transformer =
            new AnalyticsEtlTransformer(ZoneId.of("Asia/Seoul"));

    @Test
    void transformsProcessAndTaskMetricsForCurrentSchema() {
        Date processStart = Date.from(Instant.parse("2026-08-19T23:00:00Z"));
        Date taskStart = Date.from(Instant.parse("2026-08-20T00:00:00Z"));
        Date taskEnd = Date.from(Instant.parse("2026-08-20T00:30:00Z"));

        AnalyticsProcessInstanceSource instance = process(10L, processStart,
                Date.from(Instant.parse("2026-08-20T01:00:00Z")), "COMPLETED");
        AnalyticsTaskSource completedHuman = task(100L, 10L, "COMPLETED", taskStart, taskEnd,
                "user-1", "홍길동", null, null);

        AnalyticsTaskSource runningAutomated = task(101L, 10L, "RUNNING",
                Date.from(Instant.parse("2026-08-20T00:40:00Z")), null,
                null, null, "org.uengine.kernel.ServiceTask", null);

        AnalyticsTaskSource cancelledRework = task(102L, 10L, "CANCELLED",
                Date.from(Instant.parse("2026-08-20T00:50:00Z")),
                Date.from(Instant.parse("2026-08-20T00:55:00Z")),
                null, null, null, "ADMIN_BACKTOHERE");

        AnalyticsProcessInstanceFact processFact = transformer.processFact(
                instance, List.of(completedHuman, runningAutomated, cancelledRework));
        AnalyticsTaskFact taskFact = transformer.taskFact(completedHuman, instance,
                Date.from(Instant.parse("2026-08-19T23:55:00Z")));

        assertThat(processFact.getDurationSeconds()).isEqualTo(7_200L);
        assertThat(processFact.getTotalTaskCount()).isEqualTo(3);
        assertThat(processFact.getCompletedTaskCount()).isEqualTo(1);
        assertThat(processFact.getActiveTaskCount()).isEqualTo(1);
        assertThat(processFact.getCancelledTaskCount()).isEqualTo(1);
        assertThat(processFact.getHumanTaskCount()).isEqualTo(1);
        assertThat(processFact.getAutomatedTaskCount()).isEqualTo(1);
        assertThat(processFact.getReworkTaskCount()).isEqualTo(1);
        assertThat(taskFact.getDurationSeconds()).isEqualTo(1_800L);
        assertThat(taskFact.getWaitFromPreviousSeconds()).isEqualTo(300L);
        assertThat(taskFact.getLeadFromProcessSeconds()).isEqualTo(5_400L);
        assertThat(taskFact.getHumanTask()).isTrue();
        assertThat(taskFact.getAutomatedTask()).isFalse();
    }

    @Test
    void createsBusinessTimezoneDateAndClampsNegativeIntervals() {
        Date shortlyBeforeMidnightUtc = Date.from(Instant.parse("2026-08-19T23:30:00Z"));
        AnalyticsDateDimension date = transformer.dateDimension(shortlyBeforeMidnightUtc);

        AnalyticsTaskSource task = task(1L, 1L, "COMPLETED",
                Date.from(Instant.parse("2026-08-20T01:00:00Z")),
                Date.from(Instant.parse("2026-08-20T00:00:00Z")),
                null, null, null, null);
        AnalyticsTaskFact fact = transformer.taskFact(task, null,
                Date.from(Instant.parse("2026-08-20T02:00:00Z")));

        assertThat(date.getDateKey()).isEqualTo(20260820);
        assertThat(date.getCalendarDate().toString()).isEqualTo("2026-08-20");
        assertThat(fact.getDurationSeconds()).isZero();
        assertThat(fact.getWaitFromPreviousSeconds()).isZero();
    }

    @Test
    void producesStableKeysWithoutExposingBusinessIdentifiers() {
        String first = transformer.processKey("approval/process", "v1");
        String same = transformer.processKey("approval/process", "v1");
        String other = transformer.processKey("approval/process", "v2");

        assertThat(first).hasSize(32).isEqualTo(same).isNotEqualTo(other);
    }

    @Test
    void calculatesElapsedDurationForRunningProcessAtExtractionTime() {
        AnalyticsProcessInstanceSource running = process(20L,
                Date.from(Instant.parse("2026-08-20T00:00:00Z")), null, "RUNNING");

        AnalyticsProcessInstanceFact fact = transformer.processFact(running, List.of(),
                Date.from(Instant.parse("2026-08-20T00:10:00Z")));

        assertThat(fact.getDurationSeconds()).isEqualTo(600L);
        assertThat(fact.getEndDateKey()).isNull();
    }

    private AnalyticsProcessInstanceSource process(Long id, Date start, Date end, String status) {
        return new AnalyticsProcessInstanceSource(
                id, "approval", "v1", "결재", null,
                start, end, null, end, status,
                false, false, false, null, "initiator", "GROUP-A");
    }

    private AnalyticsTaskSource task(Long id, Long instanceId, String status, Date start, Date end,
                                     String endpoint, String resourceName, String tool, String decision) {
        return new AnalyticsTaskSource(
                id, instanceId, instanceId, "approval", "v1", "결재",
                "activity-1", null, "검토", null, tool,
                endpoint, resourceName, null, null,
                start, end, null, end, status, decision, null, null, false);
    }
}
