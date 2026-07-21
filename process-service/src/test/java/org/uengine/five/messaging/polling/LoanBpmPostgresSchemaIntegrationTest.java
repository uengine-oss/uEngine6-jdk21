package org.uengine.five.messaging.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.uengine.five.messaging.polling.dto.EventInboxRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PostgreSQL-only checks for the loan BPM schema alignment migration.
 *
 * <p>The class is intentionally disabled unless both an explicit JDBC URL and
 * {@code LOAN_BPM_TEST_ALLOW_WRITE=true} are provided. It inserts rows with a
 * unique T002E prefix and deletes them after every test.</p>
 */
@EnabledIfEnvironmentVariable(named = "LOAN_BPM_TEST_JDBC_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LOAN_BPM_TEST_ALLOW_WRITE", matches = "true")
class LoanBpmPostgresSchemaIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String marker = "T002E-" + UUID.randomUUID();
    private Connection connection;
    private Long processInstanceId;
    private Long worklistId;

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection(
                System.getenv("LOAN_BPM_TEST_JDBC_URL"),
                environmentOrDefault("LOAN_BPM_TEST_JDBC_USER", "uengine"),
                environmentOrDefault("LOAN_BPM_TEST_JDBC_PASSWORD", "uengine"));
    }

    @AfterEach
    void removeTestRows() throws SQLException {
        if (connection == null) {
            return;
        }
        try {
            if (worklistId != null) {
                executeUpdate("DELETE FROM bpm_worklist WHERE task_id = ?", worklistId);
            }
            if (processInstanceId != null) {
                executeUpdate("DELETE FROM bpm_procinst WHERE inst_id = ?", processInstanceId);
            }
            executeUpdate("DELETE FROM bpm_event_inbox WHERE corr_key LIKE ?", marker + "%");
        } finally {
            connection.close();
        }
    }

    @Test
    void schemaUsesCanonicalInboxSequenceAndHasNoAssignRoleColumn() throws SQLException {
        assertEquals("bigint", columnType("bpm_event_inbox", "id"));
        assertTrue(columnDefault("bpm_event_inbox", "id").contains("seq_bpm_event_inbox"));
        assertEquals("character varying", columnType("bpm_event_inbox", "corr_key"));
        assertEquals("character varying", columnType("bpm_event_inbox", "event_name"));
        assertEquals("text", columnType("bpm_event_inbox", "payload"));
        assertEquals("character varying", columnType("bpm_event_inbox", "prcr_rslt_code_nm"));
        assertEquals("text", columnType("bpm_event_inbox", "prcs_rslt_cntn"));
        assertEquals(0, columnCount("bpm_worklist", "assign_role"));
        assertEquals("NO", nullable("bpm_worklist", "delegated"));
        assertEquals("false", columnDefault("bpm_worklist", "delegated"));
    }

    @Test
    void loanAliasesRetainWholePayloadAndResultFields() throws Exception {
        String loanPcesMgmtNo = marker + "-LOAN";
        String requestJson = """
                {
                  "loanPcesMgmtNo":"%s",
                  "eventNm":"LOAN_RECEIVED",
                  "payload":{"amount":1000,"borrower":{"name":"test"}},
                  "prcsRsltCodeNm":"ACCEPTED",
                  "prcsRsltCntn":"received"
                }
                """.formatted(loanPcesMgmtNo);
        EventInboxRequest request = OBJECT_MAPPER.readValue(requestJson, EventInboxRequest.class);
        assertEquals("LOAN_RECEIVED", OBJECT_MAPPER.readValue("""
                {"evntNm":"LOAN_RECEIVED","payload":{}}
                """, EventInboxRequest.class).getEventName());
        JsonNode originalPayload = OBJECT_MAPPER.readTree(requestJson);

        Long inboxId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bpm_event_inbox
                    (corr_key, event_name, payload, prcr_rslt_code_nm, prcs_rslt_cntn)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            statement.setString(1, request.getCorrKey());
            statement.setString(2, request.getEventName());
            statement.setString(3, OBJECT_MAPPER.writeValueAsString(originalPayload));
            statement.setString(4, request.getPrcrRsltCodeNm());
            statement.setString(5, request.getPrcsRsltCntn());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                inboxId = result.getLong("id");
            }
        }

        assertNotNull(inboxId);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT corr_key, event_name, payload, prcr_rslt_code_nm, prcs_rslt_cntn
                  FROM bpm_event_inbox
                 WHERE id = ?
                """)) {
            statement.setLong(1, inboxId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(loanPcesMgmtNo, result.getString("corr_key"));
                assertEquals("LOAN_RECEIVED", result.getString("event_name"));
                JsonNode storedPayload = OBJECT_MAPPER.readTree(result.getString("payload"));
                assertEquals(originalPayload, storedPayload);
                assertEquals(loanPcesMgmtNo, storedPayload.get("loanPcesMgmtNo").asText());
                assertEquals("LOAN_RECEIVED", storedPayload.get("eventNm").asText());
                assertEquals(request.getPayload(), storedPayload.get("payload"));
                assertEquals("ACCEPTED", result.getString("prcr_rslt_code_nm"));
                assertEquals("received", result.getString("prcs_rslt_cntn"));
            }
        }
    }

    @Test
    void loanSnapshotFieldsPersistUsingCanonicalColumnNames() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bpm_procinst
                    (inst_id, adhoc, archive, deleted, dont_return, event_handler, sub_process,
                     cus_no, fncg_bswr_dvsn_code, loan_cntc_no, fncg_supt_trgt_dvsn_code,
                     loan_subj_dvsn_code, loan_hope_date, fncg_mney_usag_clsf_code, bswr_clsf_code)
                VALUES
                    (nextval('seq_bpm_procinst'), 0, 0, 0, 0, 0, 0,
                     ?, ?, ?, ?, ?, DATE '2026-07-13', ?, ?)
                RETURNING inst_id
                """)) {
            statement.setString(1, marker + "-CUS");
            statement.setString(2, "BSWR");
            statement.setString(3, "CNTC");
            statement.setString(4, "SUPT");
            statement.setString(5, "SUBJ");
            statement.setString(6, "USAGE");
            statement.setString(7, "CLASS");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                processInstanceId = result.getLong("inst_id");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT cus_no, fncg_bswr_dvsn_code, loan_cntc_no, fncg_supt_trgt_dvsn_code,
                       loan_subj_dvsn_code, loan_hope_date, fncg_mney_usag_clsf_code, bswr_clsf_code
                  FROM bpm_procinst
                 WHERE inst_id = ?
                """)) {
            statement.setLong(1, processInstanceId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(marker + "-CUS", result.getString("cus_no"));
                assertEquals("BSWR", result.getString("fncg_bswr_dvsn_code"));
                assertEquals("CNTC", result.getString("loan_cntc_no"));
                assertEquals("SUPT", result.getString("fncg_supt_trgt_dvsn_code"));
                assertEquals("SUBJ", result.getString("loan_subj_dvsn_code"));
                assertEquals("2026-07-13", result.getDate("loan_hope_date").toString());
                assertEquals("USAGE", result.getString("fncg_mney_usag_clsf_code"));
                assertEquals("CLASS", result.getString("bswr_clsf_code"));
            }
        }
    }

    @Test
    void delegatedWorklistPreservesPreviousOwnerAndStoresCurrentOwner() throws SQLException {
        processInstanceId = createMinimalProcessInstance();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bpm_worklist
                    (task_id, process_instance_inst_id, inst_id, assign_type, dispatch_option,
                     endpoint, res_name, group_cd)
                VALUES (nextval('seq_bpm_worklist'), ?, ?, 0, 0, 'old-endpoint', 'Old Owner', 'OLD-GROUP')
                RETURNING task_id
                """)) {
            statement.setLong(1, processInstanceId);
            statement.setLong(2, processInstanceId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                worklistId = result.getLong("task_id");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT delegated FROM bpm_worklist WHERE task_id = ?")) {
            statement.setLong(1, worklistId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertFalse(result.getBoolean("delegated"));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bpm_worklist
                   SET prev_endpoint = endpoint,
                       prev_user_name = res_name,
                       prev_group_cd = group_cd,
                       endpoint = 'new-endpoint',
                       res_name = 'New Owner',
                       group_cd = 'NEW-GROUP',
                       delegated = TRUE
                 WHERE task_id = ?
                """)) {
            statement.setLong(1, worklistId);
            assertEquals(1, statement.executeUpdate());
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT delegated, prev_endpoint, prev_user_name, prev_group_cd,
                       endpoint, res_name, group_cd
                  FROM bpm_worklist
                 WHERE task_id = ?
                """)) {
            statement.setLong(1, worklistId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getBoolean("delegated"));
                assertEquals("old-endpoint", result.getString("prev_endpoint"));
                assertEquals("Old Owner", result.getString("prev_user_name"));
                assertEquals("OLD-GROUP", result.getString("prev_group_cd"));
                assertEquals("new-endpoint", result.getString("endpoint"));
                assertEquals("New Owner", result.getString("res_name"));
                assertEquals("NEW-GROUP", result.getString("group_cd"));
            }
        }
    }

    private Long createMinimalProcessInstance() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bpm_procinst
                    (inst_id, adhoc, archive, deleted, dont_return, event_handler, sub_process)
                VALUES (nextval('seq_bpm_procinst'), 0, 0, 0, 0, 0, 0)
                RETURNING inst_id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong("inst_id");
            }
        }
    }

    private int columnCount(String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String columnType(String tableName, String columnName) throws SQLException {
        return columnMetadata(tableName, columnName, "data_type");
    }

    private String nullable(String tableName, String columnName) throws SQLException {
        return columnMetadata(tableName, columnName, "is_nullable");
    }

    private String columnDefault(String tableName, String columnName) throws SQLException {
        return columnMetadata(tableName, columnName, "column_default");
    }

    private String columnMetadata(String tableName, String columnName, String metadataColumn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT %s FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """.formatted(metadataColumn))) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), tableName + "." + columnName + " must exist");
                return result.getString(1);
            }
        }
    }

    private void executeUpdate(String sql, Object parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            statement.executeUpdate();
        }
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
