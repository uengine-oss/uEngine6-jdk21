package org.uengine.five.web;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.uengine.contexts.UserContext;

@RestController
@RequestMapping("/worklist/search")
public class WorklistSearchController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WorklistSearchController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/findToDo")
    public Map<String, Object> findToDo() {
        UserContext context = UserContext.getThreadLocalInstance();
        Set<String> endpointCandidates = new LinkedHashSet<>();
        addIfNotBlank(endpointCandidates, context.getUserId());
        addAll(endpointCandidates, context.getScopes());

        Set<String> scopeCandidates = new LinkedHashSet<>();
        addAll(scopeCandidates, context.getGroups());
        addAll(scopeCandidates, context.getScopes());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("endpointCandidates", fallback(endpointCandidates))
                .addValue("scopeCandidates", fallback(scopeCandidates));

        String sql = """
                select task_id, def_id, endpoint, inst_id, root_inst_id, start_date, due_date,
                       status, title, tool, trc_tag, description, role_name, scope, assign_group,
                       dispatch_option, def_name, def_ver_id
                  from bpm_worklist
                 where (
                        endpoint in (:endpointCandidates)
                     or (endpoint is null and scope in (:scopeCandidates))
                     or (dispatch_option = 1 and endpoint is null
                         and (assign_group is null or assign_group = 'null')
                         and scope in (:scopeCandidates))
                     or (dispatch_option = 1 and endpoint is null
                         and assign_group in (:scopeCandidates)
                         and (scope is null or scope = 'null' or scope in (:scopeCandidates)))
                       )
                   and (status is null or status <> 'COMPLETED')
                 order by start_date desc nulls last, task_id desc
                """;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, params, this::mapWorklist);

        Map<String, Object> embedded = new LinkedHashMap<>();
        embedded.put("worklist", rows);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("_embedded", embedded);
        return response;
    }

    private Map<String, Object> mapWorklist(ResultSet rs, int rowNum) throws SQLException {
        long taskId = rs.getLong("task_id");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", taskId);
        row.put("defId", rs.getString("def_id"));
        row.put("endpoint", rs.getString("endpoint"));
        row.put("instId", rs.getObject("inst_id"));
        row.put("rootInstId", rs.getObject("root_inst_id"));
        row.put("startDate", rs.getDate("start_date"));
        row.put("dueDate", rs.getDate("due_date"));
        row.put("status", rs.getString("status"));
        row.put("title", rs.getString("title"));
        row.put("tool", rs.getString("tool"));
        row.put("trcTag", rs.getString("trc_tag"));
        row.put("description", rs.getString("description"));
        row.put("roleName", rs.getString("role_name"));
        row.put("scope", rs.getString("scope"));
        row.put("assignGroup", rs.getString("assign_group"));
        row.put("dispatchOption", rs.getObject("dispatch_option"));
        row.put("defName", rs.getString("def_name"));
        row.put("defVerId", rs.getString("def_ver_id"));

        Map<String, Object> self = new LinkedHashMap<>();
        self.put("href", "http://localhost:9094/worklist/" + taskId);
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", self);
        row.put("_links", links);
        return row;
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            addIfNotBlank(target, value);
        }
    }

    private static void addIfNotBlank(Set<String> target, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.add(value.trim());
        }
    }

    private static List<String> fallback(Set<String> values) {
        if (values == null || values.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            fallback.add("__NO_MATCH__");
            return fallback;
        }
        return new ArrayList<>(values);
    }
}
