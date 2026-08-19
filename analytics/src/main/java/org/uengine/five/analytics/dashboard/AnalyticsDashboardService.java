package org.uengine.five.analytics.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsDashboardService {

    private static final String PERIOD_FILTER = """
            f.started_at >= :fromInstant
            AND f.started_at < :toInstant
            AND COALESCE(f.deleted, false) = false
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ZoneId zoneId;

    public AnalyticsDashboardService(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${uengine.analytics.etl.time-zone:Asia/Seoul}") String timeZone) {
        this.jdbcTemplate = jdbcTemplate;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be on or before to");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInstant", from.atStartOfDay(zoneId).toOffsetDateTime())
                .addValue("toInstant", to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime())
                .addValue("timeZone", zoneId.getId());

        AnalyticsDashboardResponse.Summary summary = summary(parameters);
        return new AnalyticsDashboardResponse(
                from,
                to,
                summary,
                statuses(parameters),
                daily(parameters),
                processes(parameters));
    }

    private AnalyticsDashboardResponse.Summary summary(MapSqlParameterSource parameters) {
        String sql = """
                SELECT COUNT(*) AS process_count,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) = 'COMPLETED') AS completed_count,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) IN ('NEW', 'RUNNING')) AS active_count,
                       COALESCE(ROUND(AVG(f.duration_seconds)), 0) AS average_duration_seconds,
                       COALESCE(SUM(f.total_task_count), 0) AS total_task_count,
                       COALESCE(SUM(f.human_task_count), 0) AS human_task_count,
                       COALESCE(SUM(f.automated_task_count), 0) AS automated_task_count,
                       COALESCE(SUM(f.rework_task_count), 0) AS rework_task_count
                  FROM bpm_fact_proc_inst f
                 WHERE %s
                """.formatted(PERIOD_FILTER);

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, parameters);
        long processCount = number(row, "process_count");
        long completedCount = number(row, "completed_count");
        long totalTaskCount = number(row, "total_task_count");
        long reworkTaskCount = number(row, "rework_task_count");
        return new AnalyticsDashboardResponse.Summary(
                processCount,
                completedCount,
                number(row, "active_count"),
                number(row, "average_duration_seconds"),
                totalTaskCount,
                number(row, "human_task_count"),
                number(row, "automated_task_count"),
                reworkTaskCount,
                percentage(completedCount, processCount),
                percentage(reworkTaskCount, totalTaskCount));
    }

    private List<AnalyticsDashboardResponse.StatusMetric> statuses(MapSqlParameterSource parameters) {
        String sql = """
                SELECT UPPER(COALESCE(NULLIF(TRIM(f.status), ''), 'UNKNOWN')) AS status,
                       COUNT(*) AS metric_count
                  FROM bpm_fact_proc_inst f
                 WHERE %s
                 GROUP BY UPPER(COALESCE(NULLIF(TRIM(f.status), ''), 'UNKNOWN'))
                 ORDER BY metric_count DESC, status
                """.formatted(PERIOD_FILTER);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new AnalyticsDashboardResponse.StatusMetric(
                        resultSet.getString("status"),
                        resultSet.getLong("metric_count")));
    }

    private List<AnalyticsDashboardResponse.DailyMetric> daily(MapSqlParameterSource parameters) {
        String sql = """
                SELECT CAST(f.started_at AT TIME ZONE 'UTC' AT TIME ZONE :timeZone AS date) AS metric_date,
                       COUNT(*) AS process_count,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) = 'COMPLETED') AS completed_count
                  FROM bpm_fact_proc_inst f
                 WHERE %s
                 GROUP BY 1
                 ORDER BY metric_date
                """.formatted(PERIOD_FILTER);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> {
            Date date = resultSet.getDate("metric_date");
            return new AnalyticsDashboardResponse.DailyMetric(
                    date.toLocalDate(),
                    resultSet.getLong("process_count"),
                    resultSet.getLong("completed_count"));
        });
    }

    private List<AnalyticsDashboardResponse.ProcessMetric> processes(MapSqlParameterSource parameters) {
        String sql = """
                SELECT f.process_key,
                       COALESCE(NULLIF(TRIM(d.definition_name), ''),
                                NULLIF(TRIM(d.definition_id), ''),
                                f.process_key,
                                'UNKNOWN') AS process_name,
                       COUNT(*) AS process_count,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) = 'COMPLETED') AS completed_count,
                       COALESCE(ROUND(AVG(f.duration_seconds)), 0) AS average_duration_seconds,
                       COALESCE(SUM(f.rework_task_count), 0) AS rework_task_count
                  FROM bpm_fact_proc_inst f
                  LEFT JOIN bpm_dim_process_def d ON d.process_key = f.process_key
                 WHERE %s
                 GROUP BY f.process_key, d.definition_name, d.definition_id
                 ORDER BY process_count DESC, process_name
                 LIMIT 10
                """.formatted(PERIOD_FILTER);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new AnalyticsDashboardResponse.ProcessMetric(
                        resultSet.getString("process_key"),
                        resultSet.getString("process_name"),
                        resultSet.getLong("process_count"),
                        resultSet.getLong("completed_count"),
                        resultSet.getLong("average_duration_seconds"),
                        resultSet.getLong("rework_task_count")));
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0.0d;
        }
        return Math.round((numerator * 10_000.0d) / denominator) / 100.0d;
    }
}
