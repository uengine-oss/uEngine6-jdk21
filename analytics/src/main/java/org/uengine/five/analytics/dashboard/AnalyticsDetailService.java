package org.uengine.five.analytics.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsDetailService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ZoneId zoneId;

    public AnalyticsDetailService(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${uengine.analytics.etl.time-zone:Asia/Seoul}") String timeZone) {
        this.jdbcTemplate = jdbcTemplate;
        this.zoneId = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> processes() {
        String sql = """
                SELECT d.process_key,
                       d.definition_id AS proc_def_id,
                       COALESCE(NULLIF(d.definition_name, ''), d.definition_id, d.process_key) AS process_name,
                       COALESCE(NULLIF(d.definition_path, ''), d.definition_id || '.bpmn') AS definition_path
                  FROM bpm_dim_process_def d
                 WHERE EXISTS (
                       SELECT 1 FROM bpm_fact_proc_inst f WHERE f.process_key = d.process_key
                 )
                 ORDER BY process_name
                """;
        return success(jdbcTemplate.queryForList(sql, Map.of()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tasksByDepartment(Integer year, Integer quarter, Integer month) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        String period = taskPeriodFilter("t", year, quarter, month, parameters);
        String sql = """
                SELECT COALESCE(NULLIF(a.group_code, ''), '미배정') AS department_name,
                       NULL::varchar AS dept_id,
                       COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE COALESCE(t.automated_task, false)) AS agent_tasks,
                       COUNT(*) FILTER (WHERE COALESCE(t.human_task, false)) AS human_tasks,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) = 'COMPLETED') AS completed_tasks,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) IN ('NEW', 'READY', 'RUNNING')) AS pending_tasks,
                       COALESCE(ROUND(AVG(t.duration_seconds)), 0) AS avg_duration_sec,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) IN ('ERROR', 'FAILED')) AS total_errors,
                       COUNT(*) FILTER (WHERE COALESCE(t.rework_task, false)) AS total_rework
                  FROM bpm_fact_task t
                  LEFT JOIN bpm_dim_actor a ON a.actor_key = t.actor_key
                 WHERE t.started_at IS NOT NULL%s
                 GROUP BY COALESCE(NULLIF(a.group_code, ''), '미배정')
                 ORDER BY total_tasks DESC, department_name
                """.formatted(period);
        return success(jdbcTemplate.queryForList(sql, parameters));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> processPerformance(Integer year, Integer quarter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        String period = processPeriodFilter("f", year, quarter, parameters);
        String sql = """
                SELECT f.process_key AS proc_def_id,
                       COALESCE(NULLIF(d.definition_name, ''), d.definition_id, f.process_key) AS process_name,
                       COUNT(*) AS total_instances,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) = 'COMPLETED') AS completed_instances,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(f.status, '')) IN ('NEW', 'RUNNING', 'STARTED')) AS running_instances,
                       COALESCE(ROUND(AVG(f.duration_seconds)), 0) AS avg_cycle_time_sec,
                       COALESCE(MIN(f.duration_seconds), 0) AS min_cycle_time_sec,
                       COALESCE(MAX(f.duration_seconds), 0) AS max_cycle_time_sec,
                       COALESCE(ROUND(percentile_cont(0.5) WITHIN GROUP (ORDER BY f.duration_seconds)), 0) AS median_cycle_time_sec,
                       COALESCE(ROUND(percentile_cont(0.95) WITHIN GROUP (ORDER BY f.duration_seconds)), 0) AS p95_cycle_time_sec,
                       COALESCE(ROUND(AVG(f.total_task_count), 1), 0) AS avg_tasks_per_instance,
                       0 AS total_errors,
                       COALESCE(SUM(f.rework_task_count), 0) AS total_rework
                  FROM bpm_fact_proc_inst f
                  LEFT JOIN bpm_dim_process_def d ON d.process_key = f.process_key
                 WHERE COALESCE(f.deleted, false) = false%s
                 GROUP BY f.process_key, d.definition_name, d.definition_id
                 ORDER BY total_instances DESC, process_name
                """.formatted(period);
        return success(jdbcTemplate.queryForList(sql, parameters));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> bottleneck(String processDefinitionId, Integer year, Integer quarter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        StringBuilder filter = new StringBuilder(taskPeriodFilter("t", year, quarter, null, parameters));
        if (processDefinitionId != null && !processDefinitionId.isBlank()) {
            parameters.addValue("processDefinitionId", stripBpmnSuffix(processDefinitionId));
            filter.append("""
                    
                      AND (d.definition_id = :processDefinitionId
                           OR regexp_replace(COALESCE(d.definition_path, ''), '\\.bpmn$', '') = :processDefinitionId
                           OR t.process_key = :processDefinitionId)
                    """);
        }
        String sql = """
                SELECT COALESCE(NULLIF(a.absolute_tracing_tag, ''), NULLIF(a.tracing_tag, ''), t.activity_key) AS activity_id,
                       COALESCE(NULLIF(a.activity_name, ''), t.activity_key) AS activity_name,
                       COALESCE(NULLIF(a.activity_type, ''), 'Task') AS activity_type,
                       COALESCE(NULLIF(d.definition_name, ''), d.definition_id, t.process_key) AS process_name,
                       COUNT(*) AS execution_count,
                       COALESCE(ROUND(AVG(t.duration_seconds)), 0) AS avg_processing_time_sec,
                       COALESCE(MIN(t.duration_seconds), 0) AS min_processing_time_sec,
                       COALESCE(MAX(t.duration_seconds), 0) AS max_processing_time_sec,
                       COALESCE(ROUND(AVG(t.wait_from_previous_seconds)), 0) AS avg_wait_time_sec,
                       COALESCE(SUM(t.duration_seconds), 0) AS total_processing_time_sec,
                       COALESCE(SUM(t.wait_from_previous_seconds), 0) AS total_wait_time_sec,
                       ROUND(100.0 * COUNT(*) FILTER (
                           WHERE UPPER(COALESCE(t.status, '')) IN ('ERROR', 'FAILED')
                              OR COALESCE(t.rework_task, false)
                       ) / NULLIF(COUNT(*), 0), 2) AS error_rate_pct
                  FROM bpm_fact_task t
                  LEFT JOIN bpm_dim_activity a ON a.activity_key = t.activity_key
                  LEFT JOIN bpm_dim_process_def d ON d.process_key = t.process_key
                 WHERE t.started_at IS NOT NULL%s
                 GROUP BY t.process_key, d.definition_name, d.definition_id,
                          a.absolute_tracing_tag, a.tracing_tag, t.activity_key,
                          a.activity_name, a.activity_type
                 ORDER BY total_wait_time_sec DESC, total_processing_time_sec DESC
                """.formatted(filter);
        return success(jdbcTemplate.queryForList(sql, parameters));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> monthlyTrend(Integer year) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        String yearFilter = "";
        if (year != null) {
            parameters.addValue("year", year);
            yearFilter = " AND EXTRACT(YEAR FROM t.started_at AT TIME ZONE :timeZone) = :year";
        }
        String sql = """
                SELECT EXTRACT(YEAR FROM t.started_at AT TIME ZONE :timeZone)::integer AS year,
                       EXTRACT(MONTH FROM t.started_at AT TIME ZONE :timeZone)::integer AS month,
                       COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE COALESCE(t.automated_task, false)) AS agent_tasks,
                       COUNT(*) FILTER (WHERE COALESCE(t.human_task, false)) AS human_tasks,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) = 'COMPLETED') AS completed_tasks,
                       COALESCE(ROUND(AVG(t.duration_seconds)), 0) AS avg_duration_sec,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) IN ('ERROR', 'FAILED')) AS total_errors,
                       COUNT(*) FILTER (WHERE COALESCE(t.rework_task, false)) AS total_rework
                  FROM bpm_fact_task t
                 WHERE t.started_at IS NOT NULL%s
                 GROUP BY 1, 2
                 ORDER BY year DESC, month DESC
                 LIMIT 12
                """.formatted(yearFilter);
        return success(jdbcTemplate.queryForList(sql, parameters));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> agentVsHuman(Integer year, Integer quarter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        String period = taskPeriodFilter("t", year, quarter, null, parameters);
        String sql = """
                SELECT CASE WHEN COALESCE(t.automated_task, false) THEN 'AGENT' ELSE 'HUMAN' END AS performer_type,
                       COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE UPPER(COALESCE(t.status, '')) = 'COMPLETED') AS completed_tasks,
                       COALESCE(ROUND(AVG(t.duration_seconds)), 0) AS avg_duration_sec,
                       0.0 AS avg_f1_score,
                       0.0 AS avg_accuracy,
                       ROUND(100.0 * COUNT(*) FILTER (
                           WHERE UPPER(COALESCE(t.status, '')) IN ('ERROR', 'FAILED')
                       ) / NULLIF(COUNT(*), 0), 2) AS error_rate_pct,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE COALESCE(t.rework_task, false))
                           / NULLIF(COUNT(*), 0), 2) AS rework_rate_pct,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE NOT COALESCE(t.rework_task, false))
                           / NULLIF(COUNT(*), 0), 2) AS first_time_right_pct
                  FROM bpm_fact_task t
                 WHERE t.started_at IS NOT NULL%s
                 GROUP BY CASE WHEN COALESCE(t.automated_task, false) THEN 'AGENT' ELSE 'HUMAN' END
                 ORDER BY performer_type
                """.formatted(period);
        return success(jdbcTemplate.queryForList(sql, parameters));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fteHeatmap(Integer year, Integer quarter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("timeZone", zoneId.getId());
        String period = taskPeriodFilter("t", year, quarter, null, parameters);
        String sql = """
                SELECT COALESCE(NULLIF(d.definition_name, ''), d.definition_id, t.process_key) AS process_name,
                       COALESCE(NULLIF(actor.group_code, ''), '미배정') AS department_name,
                       ROUND(SUM(COALESCE(t.duration_seconds, 1800)) / 576000.0, 2) AS fte,
                       COUNT(*) AS task_count
                  FROM bpm_fact_task t
                  LEFT JOIN bpm_dim_process_def d ON d.process_key = t.process_key
                  LEFT JOIN bpm_dim_actor actor ON actor.actor_key = t.actor_key
                 WHERE t.started_at IS NOT NULL%s
                 GROUP BY COALESCE(NULLIF(d.definition_name, ''), d.definition_id, t.process_key),
                          COALESCE(NULLIF(actor.group_code, ''), '미배정')
                 ORDER BY process_name, department_name
                """.formatted(period);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        List<String> processes = rows.stream().map(row -> row.get("process_name").toString()).distinct().toList();
        List<String> departments = rows.stream().map(row -> row.get("department_name").toString()).distinct().toList();
        Map<String, Map<String, Map<String, Object>>> matrix = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String process = row.get("process_name").toString();
            String department = row.get("department_name").toString();
            matrix.computeIfAbsent(process, ignored -> new LinkedHashMap<>())
                    .put(department, Map.of("fte", row.get("fte"), "task_count", row.get("task_count")));
        }
        return success(Map.of("processes", processes, "departments", departments, "matrix", matrix));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> kpiPipeline() {
        try {
            String sql = """
                    SELECT COUNT(*) AS total_processes,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'draft') AS draft_count,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'review') AS review_count,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'published') AS published_count,
                           0 AS rejected_count
                      FROM bpm_kpi_process_state
                    """;
            return jdbcTemplate.queryForMap(sql, Map.of());
        } catch (DataAccessException exception) {
            return Map.of("total_processes", 0, "draft_count", 0, "review_count", 0,
                    "published_count", 0, "rejected_count", 0);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> kpiDomainProgress() {
        try {
            String sql = """
                    SELECT domain_id,
                           domain_name,
                           COUNT(*) AS total_processes,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'published') AS published_count,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'draft') AS draft_count,
                           COUNT(*) FILTER (WHERE lifecycle_stage = 'review') AS review_count
                      FROM bpm_kpi_process_state
                     GROUP BY domain_id, domain_name
                     ORDER BY domain_name
                    """;
            return jdbcTemplate.queryForList(sql, Map.of());
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> kpiWeeklyVelocity(int weeks) {
        int safeWeeks = Math.max(1, Math.min(weeks, 52));
        try {
            String sql = """
                    WITH week_series AS (
                        SELECT generate_series(
                            date_trunc('week', CURRENT_DATE) - (:weeks - 1) * interval '1 week',
                            date_trunc('week', CURRENT_DATE),
                            interval '1 week'
                        ) AS week_start
                    )
                    SELECT to_char(w.week_start, 'YYYY-MM-DD') AS week_start,
                           COUNT(s.process_key) AS deployments
                      FROM week_series w
                      LEFT JOIN bpm_kpi_process_state s
                        ON s.lifecycle_stage = 'published'
                       AND s.deployed_at >= w.week_start
                       AND s.deployed_at < w.week_start + interval '1 week'
                     GROUP BY w.week_start
                     ORDER BY w.week_start
                    """;
            return jdbcTemplate.queryForList(sql, Map.of("weeks", safeWeeks));
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> kpiTargets() {
        try {
            String sql = """
                    SELECT target_id AS id,
                           period_type,
                           period_start::text AS period_start,
                           period_end::text AS period_end,
                           total_target
                      FROM bpm_kpi_target
                     ORDER BY period_start DESC
                    """;
            List<Map<String, Object>> targets = jdbcTemplate.queryForList(sql, Map.of());
            List<Map<String, Object>> domains = kpiDomainProgress();
            for (Map<String, Object> target : targets) {
                int totalTarget = ((Number) target.get("total_target")).intValue();
                int domainCount = Math.max(domains.size(), 1);
                List<Map<String, Object>> domainTargets = new ArrayList<>();
                for (int index = 0; index < domains.size(); index++) {
                    Map<String, Object> domain = domains.get(index);
                    int allocated = totalTarget / domainCount + (index < totalTarget % domainCount ? 1 : 0);
                    domainTargets.add(Map.of(
                            "domain_id", domain.get("domain_id"),
                            "domain_name", domain.get("domain_name"),
                            "target", allocated));
                }
                target.put("domain_targets", domainTargets);
                target.put("org_targets", List.of());
            }
            return targets;
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    @Transactional
    public Map<String, Object> upsertKpiTarget(Map<String, Object> request) {
        String targetId = String.valueOf(request.getOrDefault("id", "dashboard_target"));
        String periodType = String.valueOf(request.getOrDefault("period_type", "yearly"));
        LocalDate periodStart = LocalDate.parse(String.valueOf(
                request.getOrDefault("period_start", LocalDate.now().withDayOfYear(1))));
        LocalDate periodEnd = LocalDate.parse(String.valueOf(
                request.getOrDefault("period_end", periodStart.plusYears(1).minusDays(1))));
        int totalTarget = request.get("total_target") instanceof Number number ? number.intValue() : 0;
        String sql = """
                INSERT INTO bpm_kpi_target(
                    target_id, period_type, period_start, period_end, total_target, updated_at
                ) VALUES (
                    :targetId, :periodType, :periodStart, :periodEnd, :totalTarget, clock_timestamp()
                )
                ON CONFLICT (target_id) DO UPDATE
                SET period_type = EXCLUDED.period_type,
                    period_start = EXCLUDED.period_start,
                    period_end = EXCLUDED.period_end,
                    total_target = EXCLUDED.total_target,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("targetId", targetId)
                .addValue("periodType", periodType)
                .addValue("periodStart", periodStart)
                .addValue("periodEnd", periodEnd)
                .addValue("totalTarget", totalTarget));
        return Map.of("success", true, "id", targetId);
    }

    private String taskPeriodFilter(String alias, Integer year, Integer quarter, Integer month,
                                    MapSqlParameterSource parameters) {
        StringBuilder filter = new StringBuilder();
        if (year != null) {
            parameters.addValue("year", year);
            filter.append(" AND EXTRACT(YEAR FROM ").append(alias)
                    .append(".started_at AT TIME ZONE :timeZone) = :year");
        }
        if (quarter != null) {
            parameters.addValue("quarter", quarter);
            filter.append(" AND EXTRACT(QUARTER FROM ").append(alias)
                    .append(".started_at AT TIME ZONE :timeZone) = :quarter");
        }
        if (month != null) {
            parameters.addValue("month", month);
            filter.append(" AND EXTRACT(MONTH FROM ").append(alias)
                    .append(".started_at AT TIME ZONE :timeZone) = :month");
        }
        return filter.toString();
    }

    private String processPeriodFilter(String alias, Integer year, Integer quarter,
                                       MapSqlParameterSource parameters) {
        return taskPeriodFilter(alias, year, quarter, null, parameters);
    }

    private String stripBpmnSuffix(String value) {
        return value.trim().replaceFirst("(?i)\\.bpmn$", "");
    }

    private Map<String, Object> success(Object data) {
        return Map.of("success", true, "data", data);
    }
}
