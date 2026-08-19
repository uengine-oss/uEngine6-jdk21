package org.uengine.five.analytics.dashboard;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsDashboardResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate from,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate to,
        Summary summary,
        List<StatusMetric> statuses,
        List<DailyMetric> daily,
        List<ProcessMetric> processes) {

    public record Summary(
            long processCount,
            long completedProcessCount,
            long activeProcessCount,
            long averageDurationSeconds,
            long totalTaskCount,
            long humanTaskCount,
            long automatedTaskCount,
            long reworkTaskCount,
            double completionRate,
            double reworkRate) {
    }

    public record StatusMetric(String status, long count) {
    }

    public record DailyMetric(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
            long processCount,
            long completedProcessCount) {
    }

    public record ProcessMetric(
            String processKey,
            String processName,
            long processCount,
            long completedProcessCount,
            long averageDurationSeconds,
            long reworkTaskCount) {
    }
}
