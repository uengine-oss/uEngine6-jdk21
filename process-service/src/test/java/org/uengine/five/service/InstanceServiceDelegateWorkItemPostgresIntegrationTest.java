package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Opt-in real Process API/PostgreSQL regression for delegateOnlyForWorkitem.
 */
@EnabledIfEnvironmentVariable(named = "LOAN_BPM_T002D_API_E2E", matches = "true")
class InstanceServiceDelegateWorkItemPostgresIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROCESS_URL = "http://localhost:9094";
    private static final Path DEFINITIONS_DIRECTORY = Path.of("D:\\uEngineProject\\uEngine6-jdk21\\definitions\\test");
    private static final String ROLE_NAME = "T002D_OWNER";

    @Test
    void delegateOnlyForWorkitemUpdatesSourceAndSameLaneSiblingThenCleansUp() throws Exception {
        String jdbcUrl = requiredLocalTestJdbcUrl(System.getenv("LOAN_BPM_TEST_JDBC_URL"));
        String ownerBearer = requiredEnvironment("LOAN_BPM_T002D_OWNER_BEARER");
        String ownerEndpoint = requiredEnvironment("LOAN_BPM_T002D_OWNER_ENDPOINT");
        String ownerResourceName = requiredEnvironment("LOAN_BPM_T002D_OWNER_RESOURCE_NAME");
        String targetEndpoint = requiredEnvironment("LOAN_BPM_T002D_TARGET_ENDPOINT");
        String targetResourceName = requiredEnvironment("LOAN_BPM_T002D_TARGET_RESOURCE_NAME");
        String targetGroupCd = requiredEnvironment("LOAN_BPM_T002D_TARGET_GROUP_CD");
        String marker = "T002D-" + UUID.randomUUID();
        String definitionId = "test/" + marker;
        Path fixture = DEFINITIONS_DIRECTORY.resolve(marker + ".bpmn");
        String instanceId = null;

        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                environmentOrDefault("LOAN_BPM_TEST_JDBC_USER", "uengine"),
                environmentOrDefault("LOAN_BPM_TEST_JDBC_PASSWORD", "uengine"))) {
            assertCleanupSchema(connection);
            Files.writeString(fixture, parallelFixture(marker), StandardCharsets.UTF_8);
            assertTrue(Files.exists(fixture));

            ApiResponse started = request("POST", "/instance", ownerBearer, Map.of(
                    "processDefinitionId", definitionId,
                    "roleMappings", List.of(Map.of(
                            "name", ROLE_NAME,
                            "endpoints", List.of(ownerEndpoint),
                            "resourceNames", List.of(ownerResourceName)))));
            instanceId = started.body().path("instanceId").asText();
            assertFalse(instanceId.isBlank());

            List<WorklistRow> before = findParallelTasks(connection, Long.parseLong(instanceId));
            assertEquals(2, before.size());
            WorklistRow source = before.stream()
                    .filter(row -> "Task_A".equals(row.tracingTag()))
                    .findFirst()
                    .orElseThrow();
            WorklistRow sibling = before.stream()
                    .filter(row -> "Task_B".equals(row.tracingTag()))
                    .findFirst()
                    .orElseThrow();

            ApiResponse toDo = request("GET", "/worklist/search/findToDo", ownerBearer, null);
            String toDoEvidence = findToDoEvidence(toDo, source, sibling);
            assertTrue(containsTask(toDo.body().path("_embedded").path("worklist"), source.taskId()), toDoEvidence);
            assertTrue(containsTask(toDo.body().path("_embedded").path("worklist"), sibling.taskId()), toDoEvidence);

            ApiResponse delegated = request("POST",
                    "/work-item/" + source.taskId() + "/delegate?delegateOnlyForWorkitem=true",
                    ownerBearer,
                    Map.of(
                            "endpoint", targetEndpoint,
                            "resourceName", targetResourceName,
                            "targetType", "USER"));
            assertDelegatedResponse(delegated.body().path("worklist"), source,
                    targetEndpoint, targetResourceName, targetGroupCd);

            Map<Long, WorklistRow> after = findParallelTasks(connection, Long.parseLong(instanceId)).stream()
                    .collect(java.util.stream.Collectors.toMap(WorklistRow::taskId, row -> row));
            assertDelegatedRow(after.get(source.taskId()), source, targetEndpoint, targetResourceName, targetGroupCd);
            assertDelegatedRow(after.get(sibling.taskId()), sibling, targetEndpoint, targetResourceName, targetGroupCd);
        } finally {
            try {
                if (instanceId != null) {
                    int status = deleteInstance(ownerBearer, instanceId);
                    assertTrue(status >= 200 && status < 300, "DELETE /instance must succeed: " + status);
                }
                try (Connection connection = DriverManager.getConnection(
                        jdbcUrl,
                        environmentOrDefault("LOAN_BPM_TEST_JDBC_USER", "uengine"),
                        environmentOrDefault("LOAN_BPM_TEST_JDBC_PASSWORD", "uengine"))) {
                    assertMarkerRowsRemoved(connection, definitionId, instanceId);
                }
            } finally {
                Files.deleteIfExists(fixture);
                assertFalse(Files.exists(fixture));
            }
        }
    }

    private static ApiResponse request(String method, String path, String bearer, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(PROCESS_URL + path))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)));
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                method + " " + path + " failed: " + response.statusCode() + " " + response.body());
        return new ApiResponse(response.statusCode(), OBJECT_MAPPER.readTree(response.body()));
    }

    private static int deleteInstance(String bearer, String instanceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROCESS_URL + "/instance/" + instanceId))
                .header("Authorization", "Bearer " + bearer)
                .DELETE()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static List<WorklistRow> findParallelTasks(Connection connection, Long instanceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT task_id, trc_tag, endpoint, res_name, group_cd, delegated,
                       prev_endpoint, prev_user_name, prev_group_cd
                  FROM bpm_worklist
                 WHERE inst_id = ? AND role_name = ? AND trc_tag IN ('Task_A', 'Task_B')
                 ORDER BY task_id
                """)) {
            statement.setLong(1, instanceId);
            statement.setString(2, ROLE_NAME);
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<WorklistRow> rows = new java.util.ArrayList<>();
                while (result.next()) {
                    rows.add(new WorklistRow(
                            result.getLong("task_id"),
                            result.getString("trc_tag"),
                            result.getString("endpoint"),
                            result.getString("res_name"),
                            result.getString("group_cd"),
                            result.getBoolean("delegated"),
                            result.getString("prev_endpoint"),
                            result.getString("prev_user_name"),
                            result.getString("prev_group_cd")));
                }
                return rows;
            }
        }
    }

    private static void assertDelegatedResponse(JsonNode worklist, WorklistRow before,
            String targetEndpoint, String targetResourceName, String targetGroupCd) {
        assertNotNull(worklist);
        assertTrue(worklist.path("delegated").asBoolean());
        assertEquals(before.endpoint(), worklist.path("prevEndpoint").asText());
        assertEquals(before.resourceName(), worklist.path("prevUserName").asText());
        assertEquals(before.groupCd(), nullableText(worklist, "prevGroupCd"));
        assertEquals(targetEndpoint, worklist.path("endpoint").asText());
        assertEquals(targetResourceName, worklist.path("resName").asText());
        assertEquals(targetGroupCd, nullableText(worklist, "groupCd"));
    }

    private static void assertDelegatedRow(WorklistRow actual, WorklistRow before,
            String targetEndpoint, String targetResourceName, String targetGroupCd) {
        assertNotNull(actual);
        assertTrue(actual.delegated());
        assertEquals(before.endpoint(), actual.prevEndpoint());
        assertEquals(before.resourceName(), actual.prevUserName());
        assertEquals(before.groupCd(), actual.prevGroupCd());
        assertEquals(targetEndpoint, actual.endpoint());
        assertEquals(targetResourceName, actual.resourceName());
        assertEquals(targetGroupCd, actual.groupCd());
    }

    private static boolean containsTask(JsonNode worklist, Long taskId) {
        for (JsonNode item : worklist) {
            if (item.path("taskId").asLong() == taskId) return true;
        }
        return false;
    }

    private static String findToDoEvidence(ApiResponse response, WorklistRow source, WorklistRow sibling) {
        JsonNode rows = response.body().path("_embedded").path("worklist");
        return "findToDo status=" + response.statusCode()
                + ", totalRows=" + (rows.isArray() ? rows.size() : 0)
                + ", sourcePresent=" + containsTask(rows, source.taskId())
                + ", siblingPresent=" + containsTask(rows, sibling.taskId())
                + ", source=" + markerWorklistEvidence(source)
                + ", sibling=" + markerWorklistEvidence(sibling);
    }

    private static String markerWorklistEvidence(WorklistRow row) {
        return "{taskId=" + row.taskId()
                + ", endpoint=" + redact(row.endpoint())
                + ", resName=" + redact(row.resourceName())
                + ", groupCd=" + redact(row.groupCd()) + "}";
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        return value.length() == 1 ? "*" : value.substring(0, 1) + "***";
    }

    private static void assertCleanupSchema(Connection connection) throws SQLException {
        assertTableColumn(connection, "bpm_procinst", "inst_id");
        assertTableColumn(connection, "bpm_worklist", "def_id");
        assertTableColumn(connection, "bpm_rolemapping", "inst_id");
        assertTableColumn(connection, "bpm_audit", "root_inst_id");
    }

    private static void assertMarkerRowsRemoved(Connection connection, String definitionId, String instanceId) throws SQLException {
        assertCleanupSchema(connection);
        assertEquals(0L, count(connection, "SELECT count(*) FROM bpm_worklist WHERE def_id = ?", definitionId));
        if (instanceId != null) {
            Long id = Long.valueOf(instanceId);
            assertEquals(0L, count(connection, "SELECT count(*) FROM bpm_procinst WHERE inst_id = ?", id));
            assertEquals(0L, count(connection, "SELECT count(*) FROM bpm_rolemapping WHERE inst_id = ?", id));
            assertEquals(0L, count(connection, "SELECT count(*) FROM bpm_audit WHERE root_inst_id = ?", id));
        }
    }

    private static void assertTableColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*)
                  FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals(1L, result.getLong(1), table + "." + column + " is required for cleanup verification");
            }
        }
    }

    private static long count(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof String) {
                statement.setString(1, (String) value);
            } else {
                statement.setLong(1, (Long) value);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String nullableText(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for T002D API E2E");
        return value;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredLocalTestJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalStateException("LOAN_BPM_TEST_JDBC_URL must be jdbc:postgresql://localhost:5432/uengine");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        boolean localHost = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || !localHost || uri.getPort() != 5432
                || !"/uengine".equals(uri.getPath()) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("LOAN_BPM_TEST_JDBC_URL must be jdbc:postgresql://localhost:5432/uengine");
        }
        return jdbcUrl;
    }

    private static String parallelFixture(String marker) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  xmlns:uengine="http://uengine"
                                  id="Definitions_%s" targetNamespace="http://uengine.org/test">
                  <bpmn:process id="test/%s" name="%s" isExecutable="true">
                    <bpmn:laneSet id="LaneSet_1">
                      <bpmn:lane id="Lane_OWNER" name="T002D_OWNER">
                        <bpmn:flowNodeRef>Task_A</bpmn:flowNodeRef><bpmn:flowNodeRef>Task_B</bpmn:flowNodeRef>
                      </bpmn:lane>
                    </bpmn:laneSet>
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:parallelGateway id="Gateway_Split"/>
                    <bpmn:userTask id="Task_A" name="T002D A">
                      <bpmn:extensionElements><uengine:properties><uengine:json>{"eventSynchronization":{"mappingContext":{"mappingElements":[]},"eventType":"","attributes":[]}}</uengine:json></uengine:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:userTask id="Task_B" name="T002D B">
                      <bpmn:extensionElements><uengine:properties><uengine:json>{"eventSynchronization":{"mappingContext":{"mappingElements":[]},"eventType":"","attributes":[]}}</uengine:json></uengine:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:parallelGateway id="Gateway_Join"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Gateway_Split"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Gateway_Split" targetRef="Task_A"/>
                    <bpmn:sequenceFlow id="Flow_3" sourceRef="Gateway_Split" targetRef="Task_B"/>
                    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_A" targetRef="Gateway_Join"/>
                    <bpmn:sequenceFlow id="Flow_5" sourceRef="Task_B" targetRef="Gateway_Join"/>
                    <bpmn:sequenceFlow id="Flow_6" sourceRef="Gateway_Join" targetRef="EndEvent_1"/>
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="test/%s">
                    <bpmndi:BPMNShape id="Lane_di" bpmnElement="Lane_OWNER"><dc:Bounds x="100" y="80" width="900" height="280"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Start_di" bpmnElement="StartEvent_1"><dc:Bounds x="140" y="190" width="36" height="36"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Split_di" bpmnElement="Gateway_Split"><dc:Bounds x="230" y="183" width="50" height="50"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="TaskA_di" bpmnElement="Task_A"><dc:Bounds x="360" y="120" width="150" height="80"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="TaskB_di" bpmnElement="Task_B"><dc:Bounds x="360" y="250" width="150" height="80"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="Join_di" bpmnElement="Gateway_Join"><dc:Bounds x="650" y="183" width="50" height="50"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNShape id="End_di" bpmnElement="EndEvent_1"><dc:Bounds x="790" y="190" width="36" height="36"/></bpmndi:BPMNShape>
                    <bpmndi:BPMNEdge id="Flow1_di" bpmnElement="Flow_1"><di:waypoint x="176" y="208"/><di:waypoint x="230" y="208"/></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow2_di" bpmnElement="Flow_2"><di:waypoint x="280" y="208"/><di:waypoint x="360" y="160"/></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow3_di" bpmnElement="Flow_3"><di:waypoint x="280" y="208"/><di:waypoint x="360" y="290"/></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow4_di" bpmnElement="Flow_4"><di:waypoint x="510" y="160"/><di:waypoint x="650" y="208"/></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow5_di" bpmnElement="Flow_5"><di:waypoint x="510" y="290"/><di:waypoint x="650" y="208"/></bpmndi:BPMNEdge>
                    <bpmndi:BPMNEdge id="Flow6_di" bpmnElement="Flow_6"><di:waypoint x="700" y="208"/><di:waypoint x="790" y="208"/></bpmndi:BPMNEdge>
                  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """.formatted(marker, marker, marker, marker);
    }

    private record WorklistRow(Long taskId, String tracingTag, String endpoint, String resourceName,
            String groupCd, boolean delegated, String prevEndpoint, String prevUserName, String prevGroupCd) {
    }

    private record ApiResponse(int statusCode, JsonNode body) {
    }
}

