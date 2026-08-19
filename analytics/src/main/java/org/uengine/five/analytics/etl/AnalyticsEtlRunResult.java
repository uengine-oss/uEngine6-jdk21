package org.uengine.five.analytics.etl;

import java.time.Instant;

public record AnalyticsEtlRunResult(
        Instant startedAt,
        Instant finishedAt,
        int sourceProcessInstances,
        int sourceTasks,
        int processDefinitions,
        int activities,
        int actors,
        int dates,
        int processFacts,
        int taskFacts) {
}
