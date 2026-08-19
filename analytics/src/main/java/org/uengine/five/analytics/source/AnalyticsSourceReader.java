package org.uengine.five.analytics.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Repository
public class AnalyticsSourceReader {

    private final JdbcTemplate jdbcTemplate;
    private final String processTable;
    private final String worklistTable;

    public AnalyticsSourceReader(
            JdbcTemplate jdbcTemplate,
            @Value("${uengine.analytics.source-schema:public}") String sourceSchema) {
        if (sourceSchema == null || !sourceSchema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid analytics source schema: " + sourceSchema);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.processTable = sourceSchema + ".bpm_procinst";
        this.worklistTable = sourceSchema + ".bpm_worklist";
    }

    public List<AnalyticsProcessInstanceSource> processInstances() {
        String sql = """
                SELECT inst_id, def_id, def_ver_id, def_name, def_path,
                       started_date, finished_date, due_date, mod_date, status,
                       deleted, sub_process, event_handler, root_inst_id,
                       init_ep, init_group_cd
                  FROM %s
                 ORDER BY inst_id
                """.formatted(processTable);
        return jdbcTemplate.query(sql, this::processInstance);
    }

    public List<AnalyticsTaskSource> tasks() {
        String sql = """
                SELECT task_id, inst_id, root_inst_id, def_id, def_ver_id, def_name,
                       trc_tag, abs_trc_tag, title, act_type, tool,
                       endpoint, res_name, group_cd, role_name,
                       start_date, end_date, due_date, save_date,
                       status, decision, reason, priority, delegated
                  FROM %s
                 ORDER BY inst_id, start_date NULLS LAST, task_id
                """.formatted(worklistTable);
        return jdbcTemplate.query(sql, this::task);
    }

    private AnalyticsProcessInstanceSource processInstance(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnalyticsProcessInstanceSource(
                nullableLong(resultSet, "inst_id"),
                resultSet.getString("def_id"),
                resultSet.getString("def_ver_id"),
                resultSet.getString("def_name"),
                resultSet.getString("def_path"),
                date(resultSet, "started_date"),
                date(resultSet, "finished_date"),
                date(resultSet, "due_date"),
                date(resultSet, "mod_date"),
                resultSet.getString("status"),
                booleanValue(resultSet.getObject("deleted")),
                booleanValue(resultSet.getObject("sub_process")),
                booleanValue(resultSet.getObject("event_handler")),
                nullableLong(resultSet, "root_inst_id"),
                resultSet.getString("init_ep"),
                resultSet.getString("init_group_cd"));
    }

    private AnalyticsTaskSource task(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnalyticsTaskSource(
                nullableLong(resultSet, "task_id"),
                nullableLong(resultSet, "inst_id"),
                nullableLong(resultSet, "root_inst_id"),
                resultSet.getString("def_id"),
                resultSet.getString("def_ver_id"),
                resultSet.getString("def_name"),
                resultSet.getString("trc_tag"),
                resultSet.getString("abs_trc_tag"),
                resultSet.getString("title"),
                resultSet.getString("act_type"),
                resultSet.getString("tool"),
                resultSet.getString("endpoint"),
                resultSet.getString("res_name"),
                resultSet.getString("group_cd"),
                resultSet.getString("role_name"),
                date(resultSet, "start_date"),
                date(resultSet, "end_date"),
                date(resultSet, "due_date"),
                date(resultSet, "save_date"),
                resultSet.getString("status"),
                resultSet.getString("decision"),
                resultSet.getString("reason"),
                nullableInteger(resultSet, "priority"),
                booleanValue(resultSet.getObject("delegated")));
    }

    private Date date(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : new Date(timestamp.getTime());
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        Number number = (Number) resultSet.getObject(column);
        return number == null ? null : number.longValue();
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Number number = (Number) resultSet.getObject(column);
        return number == null ? null : number.intValue();
    }

    private boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        return "true".equalsIgnoreCase(text) || "t".equalsIgnoreCase(text)
                || "y".equalsIgnoreCase(text) || "1".equals(text);
    }
}
