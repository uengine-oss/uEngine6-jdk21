package org.uengine.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.uengine.five.serializers.BpmnXMLParser;
import org.uengine.kernel.AbstractProcessInstance;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.MappingElement;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ValidationContext;
import org.uengine.kernel.bpmn.SQLTask;
import org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy;
import org.uengine.kernel.bpmn.sql.DirectSQLStrategy;
import org.uengine.kernel.bpmn.sql.QueryMode;
import org.uengine.util.dao.ConnectionFactory;
import org.uengine.util.dao.JDBCConnectionFactory;

/**
 * Modeling-time test for {@link SQLTask}.
 *
 * <p>
 * A SQLTask is modeled as a {@code <bpmn:serviceTask/>} whose
 * {@code uengine:properties} JSON declares
 * {@code "_type": "org.uengine.kernel.bpmn.SQLTask"}. This verifies that such a
 * BPMN file deserializes into a fully configured task for both configuration
 * styles (hand-written SQL and database mapping), that design-time validation
 * reports misconfiguration, and finally that the parsed definition really runs.
 * </p>
 */
public class SQLTaskModelingTest {

    static final String BPMN_PATH = "src/test/resources/bpmn/sqlTask.bpmn";
    static final String JDBC_URL = "jdbc:h2:mem:sqltaskmodeling;DB_CLOSE_DELAY=-1";

    ConnectionFactory connectionFactory;

    @Before
    public void setUp() throws Exception {
        AbstractProcessInstance.USE_CLASS = DefaultProcessInstance.class;

        connectionFactory = new JDBCConnectionFactory("org.h2.Driver", JDBC_URL, "sa", "");

        Connection connection = connectionFactory.getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.execute("drop table if exists CUSTOMER");
            statement.execute("create table CUSTOMER ("
                    + " ID varchar(20) primary key,"
                    + " NAME varchar(100),"
                    + " CREDIT_LIMIT bigint)");
            statement.close();
        } finally {
            connectionFactory.releaseConnection(connection);
        }
    }

    @Test
    public void testDirectSqlTaskIsParsedFromBpmn() throws Exception {

        ProcessDefinition definition = parse();

        SQLTask sqlTask = (SQLTask) definition.getActivity("SQLTask_Insert");
        assertNotNull("SQLTask_Insert should be parsed", sqlTask);
        assertEquals("고객 등록", sqlTask.getName());

        assertTrue("no strategy configured means hand-written SQL",
                sqlTask.getStrategyOrDefault() instanceof DirectSQLStrategy);
        assertEquals("insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, upper(?), ?)", sqlTask.getSqlStmt());

        assertEquals(3, sqlTask.getParameters().length);
        assertEquals("ID", sqlTask.getParameters()[0].getArgument().getText());
        assertEquals("customerId", sqlTask.getParameters()[0].getVariable().getName());
        assertEquals("CREDIT_LIMIT", sqlTask.getParameters()[2].getArgument().getText());
        assertEquals("creditLimit", sqlTask.getParameters()[2].getVariable().getName());

        assertTrue(sqlTask.getConnectionFactory() instanceof JDBCConnectionFactory);
        assertEquals(JDBC_URL, ((JDBCConnectionFactory) sqlTask.getConnectionFactory()).getConnectionString());
        assertEquals("org.h2.Driver", ((JDBCConnectionFactory) sqlTask.getConnectionFactory()).getDriverClass());
    }

    @Test
    public void testDatabaseMappingStrategyIsParsedFromBpmn() throws Exception {

        ProcessDefinition definition = parse();

        SQLTask sqlTask = (SQLTask) definition.getActivity("SQLTask_SelectByMapping");
        assertNotNull("SQLTask_SelectByMapping should be parsed", sqlTask);

        assertTrue("the same task type, configured the DatabaseMapping way",
                sqlTask.getStrategy() instanceof DatabaseMappingStrategy);

        DatabaseMappingStrategy strategy = (DatabaseMappingStrategy) sqlTask.getStrategy();
        assertEquals(QueryMode.SELECT, strategy.getQueryMode());

        MappingElement[] mappingElements = strategy.getMappingContext().getMappingElements();
        assertEquals(3, mappingElements.length);
        assertEquals("CUSTOMER.ID", mappingElements[0].getArgument().getText());
        assertEquals("customerId", mappingElements[0].getVariable().getName());
        assertTrue("CUSTOMER.ID is the key column", mappingElements[0].isKey());
        assertTrue("CUSTOMER.NAME is not a key column", !mappingElements[1].isKey());
    }

    /**
     * Design-time feedback: the modeler is told when the mapped parameters do not
     * match the placeholders of the statement.
     */
    @Test
    public void testValidationReportsParameterMismatch() throws Exception {

        ProcessDefinition definition = parse();

        SQLTask sqlTask = (SQLTask) definition.getActivity("SQLTask_Insert");
        sqlTask.setSqlStmt("insert into CUSTOMER (ID, NAME) values (?, ?)");

        ValidationContext validationContext = sqlTask.validate(new HashMap());

        assertTrue("a 3-parameter mapping against 2 placeholders must be reported",
                messagesOf(validationContext).contains("placeholder"));
    }

    @Test
    public void testValidationReportsMissingConnectionFactory() throws Exception {

        ProcessDefinition definition = parse();

        SQLTask sqlTask = (SQLTask) definition.getActivity("SQLTask_Insert");
        sqlTask.setConnectionFactory(null);

        assertTrue(messagesOf(sqlTask.validate(new HashMap())).contains("connectionFactory"));
    }

    /**
     * The legacy {@code DatabaseMappingActivity} used int query modes; a
     * definition authored back then still deserializes.
     */
    @Test
    public void testLegacyIntQueryModeIsAccepted() throws Exception {
        assertEquals(QueryMode.SELECT, QueryMode.fromJson(Integer.valueOf(1)));
        assertEquals(QueryMode.INSERT, QueryMode.fromJson(Integer.valueOf(2)));
        assertEquals(QueryMode.UPDATE, QueryMode.fromJson(Integer.valueOf(3)));
        assertEquals(QueryMode.DELETE, QueryMode.fromJson(Integer.valueOf(4)));
        assertEquals(QueryMode.UPDATE, QueryMode.fromJson("update"));
    }

    /**
     * Modeling time meets run time: the very definition parsed out of the BPMN
     * file is executed and both tasks hit the database.
     */
    @Test
    public void testParsedDefinitionExecutes() throws Exception {

        ProcessDefinition definition = parse();
        definition.afterDeserialization();

        ProcessInstance instance = definition.createInstance();
        instance.set("", "customerId", "C-100");
        instance.set("", "customerName", "uEngine BMT");
        instance.set("", "creditLimit", Long.valueOf(3000));

        instance.execute();

        // the direct-SQL task inserted the row - the statement upper-cases the name,
        // so what lands in the database differs from the input variable
        assertEquals("UENGINE BMT", queryForString("select NAME from CUSTOMER where ID = 'C-100'"));

        // ... and the database-mapping task read it back into the process variables
        assertEquals("UENGINE BMT", instance.get("", "customerName"));
        assertEquals(3000L, ((Number) instance.get("", "creditLimit")).longValue());

        assertEquals(Activity.STATUS_COMPLETED,
                definition.getActivity("SQLTask_SelectByMapping").getStatus(instance));
    }

    /**
     * The statement the mapping produces: key columns become the where clause,
     * the remaining ones become the select list.
     */
    @Test
    public void testDatabaseMappingGeneratesSelectStatement() throws Exception {

        ProcessDefinition definition = parse();
        definition.afterDeserialization();

        SQLTask selectTask = (SQLTask) definition.getActivity("SQLTask_SelectByMapping");

        assertEquals("select NAME, CREDIT_LIMIT from CUSTOMER where ID = ?",
                selectTask.getStrategy().prepare(selectTask, definition.createInstance()).getSql());
    }

    /**
     * The modeler loads and saves a definition as typed JSON; a SQLTask has to
     * survive that round trip with its polymorphic connectionFactory/strategy.
     */
    @Test
    public void testTypedJsonRoundTrip() throws Exception {

        ProcessDefinition definition = parse();
        SQLTask original = (SQLTask) definition.getActivity("SQLTask_SelectByMapping");

        ObjectMapper objectMapper = BpmnXMLParser.createTypedJsonObjectMapper();
        // written the way the definition writes its child activities: as Activity
        String json = objectMapper.writerFor(Activity.class).writeValueAsString(original);

        assertTrue("the task type must be written out", json.contains("org.uengine.kernel.bpmn.SQLTask"));
        assertTrue("the strategy type must be written out",
                json.contains("org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy"));

        SQLTask restored = (SQLTask) objectMapper.readValue(json, Activity.class);

        assertTrue(restored.getStrategy() instanceof DatabaseMappingStrategy);
        assertEquals(QueryMode.SELECT, ((DatabaseMappingStrategy) restored.getStrategy()).getQueryMode());
        assertEquals(3, ((DatabaseMappingStrategy) restored.getStrategy()).getMappingContext()
                .getMappingElements().length);
        assertTrue(restored.getConnectionFactory() instanceof JDBCConnectionFactory);
        assertEquals(JDBC_URL, ((JDBCConnectionFactory) restored.getConnectionFactory()).getConnectionString());
    }

    // ------------------------------------------------------------------

    private ProcessDefinition parse() throws Exception {
        String bpmn = new String(Files.readAllBytes(Paths.get(BPMN_PATH)), StandardCharsets.UTF_8);

        return new BpmnXMLParser().parse(bpmn);
    }

    private static String messagesOf(ValidationContext validationContext) {
        StringBuilder messages = new StringBuilder();

        for (Object leveledException : validationContext) {
            messages.append(leveledException).append("\n");
        }

        return messages.toString();
    }

    private String queryForString(String sql) throws Exception {
        Connection connection = connectionFactory.getConnection();
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);
            String value = resultSet.next() ? resultSet.getString(1) : null;
            resultSet.close();
            statement.close();

            return value;
        } finally {
            connectionFactory.releaseConnection(connection);
        }
    }
}
