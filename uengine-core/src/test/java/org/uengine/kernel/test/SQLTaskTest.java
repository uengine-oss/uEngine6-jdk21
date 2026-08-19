package org.uengine.kernel.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.uengine.contexts.MappingContext;
import org.uengine.contexts.TextContext;
import org.uengine.kernel.AbstractProcessInstance;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.MappingElement;
import org.uengine.kernel.ParameterContext;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ProcessVariable;
import org.uengine.kernel.ProcessVariableValue;
import org.uengine.kernel.bpmn.SQLTask;
import org.uengine.kernel.bpmn.StartEvent;
import org.uengine.kernel.bpmn.SequenceFlow;
import org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy;
import org.uengine.kernel.bpmn.sql.DirectSQLStrategy;
import org.uengine.kernel.bpmn.sql.QueryMode;
import org.uengine.util.dao.ConnectionFactory;
import org.uengine.util.dao.JDBCConnectionFactory;

/**
 * Runtime test for {@link SQLTask}: a real (in-memory H2) database is created,
 * a process containing a SQLTask is executed and the effect on both the
 * database and the process variables is asserted - for the hand-written-SQL
 * strategy as well as for the database-mapping strategy.
 */
public class SQLTaskTest extends UEngineTest {

    static final String JDBC_URL = "jdbc:h2:mem:sqltasktest;DB_CLOSE_DELAY=-1";

    ConnectionFactory connectionFactory;

    @Override
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

    // ------------------------------------------------------------------
    // DirectSQLStrategy - the modeler writes the statement (old SQLActivity)
    // ------------------------------------------------------------------

    public void testDirectSqlInsert() throws Exception {

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setSqlStmt("insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, ?, ?)");
        sqlTask.setParameters(new ParameterContext[] {
                parameter("ID", "customerId"),
                parameter("NAME", "customerName"),
                parameter("CREDIT_LIMIT", "creditLimit")
        });

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.set("", "customerName", "uEngine");
        instance.set("", "creditLimit", Long.valueOf(1000));
        instance.execute();

        assertEquals("uEngine", queryForString("select NAME from CUSTOMER where ID = 'C-1'"));
        assertEquals("1000", queryForString("select CREDIT_LIMIT from CUSTOMER where ID = 'C-1'"));
        assertEquals(Activity.STATUS_COMPLETED, sqlTask.getStatus(instance));
    }

    public void testDirectSqlSelectBindsResultIntoProcessVariables() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setQuery(true);
        sqlTask.setSqlStmt("select NAME, CREDIT_LIMIT from CUSTOMER where ID = ?");
        sqlTask.setParameters(new ParameterContext[] { parameter("ID", "customerId") });
        sqlTask.setSelectMappings(new ParameterContext[] {
                parameter("NAME", "customerName"),
                parameter("CREDIT_LIMIT", "creditLimit")
        });

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.execute();

        assertEquals("uEngine", instance.get("", "customerName"));
        assertEquals(Long.valueOf(1000), instance.get("", "creditLimit"));
    }

    /**
     * More than one row selected and applySingleValueOnly off - every mapped
     * variable becomes a multiple-valued process variable.
     */
    public void testDirectSqlSelectBindsMultipleRows() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");
        update("insert into CUSTOMER values ('C-2', 'OpenCloudEngine', 2000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setQuery(true);
        sqlTask.setSqlStmt("select NAME from CUSTOMER order by ID");
        sqlTask.setSelectMappings(new ParameterContext[] { parameter("NAME", "customerName") });

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.execute();

        ProcessVariableValue names = instance.getMultiple("", "customerName");
        assertEquals(2, names.size());
        names.beforeFirst();
        assertEquals("uEngine", names.getValue());
        assertTrue(names.next());
        assertEquals("OpenCloudEngine", names.getValue());
    }

    /**
     * A parameter holding several values runs the statement once per value.
     */
    public void testDirectSqlInsertRunsOncePerParameterValue() throws Exception {

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setSqlStmt("insert into CUSTOMER (ID, NAME) values (?, ?)");
        sqlTask.setParameters(new ParameterContext[] {
                parameter("ID", "customerId"),
                parameter("NAME", "customerName")
        });

        ProcessInstance instance = instanceOf(definition, sqlTask);

        ProcessVariableValue ids = multipleValue("customerId", new String[] { "C-1", "C-2", "C-3" });
        ProcessVariableValue names = multipleValue("customerName", new String[] { "a", "b", "c" });
        instance.set("", ids);
        instance.set("", names);

        instance.execute();

        assertEquals("3", queryForString("select count(*) from CUSTOMER"));
        assertEquals("b", queryForString("select NAME from CUSTOMER where ID = 'C-2'"));
    }

    /**
     * The statement itself may contain a {@code <% %>} template resolved against
     * the instance.
     */
    public void testDirectSqlEvaluatesTemplateInStatement() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setQuery(true);
        sqlTask.setSqlStmt("select NAME from CUSTOMER where ID = '<%customerId%>'");
        sqlTask.setSelectMappings(new ParameterContext[] { parameter("NAME", "customerName") });

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.execute();

        assertEquals("uEngine", instance.get("", "customerName"));
    }

    public void testNoConnectionFactoryIsReported() throws Exception {

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = new SQLTask();
        sqlTask.setTracingTag("sql");
        sqlTask.setSqlStmt("select 1 from CUSTOMER");

        ProcessInstance instance = instanceOf(definition, sqlTask);

        try {
            instance.execute();
            fail("a SQLTask without a connectionFactory should fail");
        } catch (Exception expected) {
            assertTrue(String.valueOf(expected.getMessage()) + " / " + rootCauseMessage(expected),
                    rootCauseMessage(expected).contains("connectionFactory"));
        }
    }

    // ------------------------------------------------------------------
    // DatabaseMappingStrategy - the SQL is generated from a column mapping
    // (revival of the old DatabaseMappingActivity)
    // ------------------------------------------------------------------

    public void testDatabaseMappingInsert() throws Exception {

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setStrategy(databaseMapping(QueryMode.INSERT));

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-9");
        instance.set("", "customerName", "mapped");
        instance.set("", "creditLimit", Long.valueOf(500));
        instance.execute();

        assertEquals("mapped", queryForString("select NAME from CUSTOMER where ID = 'C-9'"));
        assertEquals("500", queryForString("select CREDIT_LIMIT from CUSTOMER where ID = 'C-9'"));
    }

    public void testDatabaseMappingSelectByKey() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");
        update("insert into CUSTOMER values ('C-2', 'other', 2000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setStrategy(databaseMapping(QueryMode.SELECT));

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.execute();

        assertEquals("uEngine", instance.get("", "customerName"));
        assertEquals(Long.valueOf(1000), instance.get("", "creditLimit"));
    }

    public void testDatabaseMappingUpdateByKey() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");
        update("insert into CUSTOMER values ('C-2', 'untouched', 2000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setStrategy(databaseMapping(QueryMode.UPDATE));

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.set("", "customerName", "renamed");
        instance.set("", "creditLimit", Long.valueOf(9999));
        instance.execute();

        assertEquals("renamed", queryForString("select NAME from CUSTOMER where ID = 'C-1'"));
        assertEquals("9999", queryForString("select CREDIT_LIMIT from CUSTOMER where ID = 'C-1'"));
        assertEquals("untouched", queryForString("select NAME from CUSTOMER where ID = 'C-2'"));
    }

    public void testDatabaseMappingDeleteByKey() throws Exception {

        update("insert into CUSTOMER values ('C-1', 'uEngine', 1000)");
        update("insert into CUSTOMER values ('C-2', 'untouched', 2000)");

        ProcessDefinition definition = definitionWithVariables();

        SQLTask sqlTask = newSqlTask();
        sqlTask.setStrategy(databaseMapping(QueryMode.DELETE));

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-1");
        instance.execute();

        assertEquals("1", queryForString("select count(*) from CUSTOMER"));
        assertEquals("untouched", queryForString("select NAME from CUSTOMER where ID = 'C-2'"));
    }

    /**
     * The two strategies are interchangeable on the very same task: swapping the
     * strategy changes only how the statement is produced.
     */
    public void testStrategyIsSwappableOnTheSameTask() throws Exception {

        SQLTask sqlTask = newSqlTask();
        assertTrue(sqlTask.getStrategyOrDefault() instanceof DirectSQLStrategy);

        sqlTask.setStrategy(databaseMapping(QueryMode.INSERT));
        assertTrue(sqlTask.getStrategyOrDefault() instanceof DatabaseMappingStrategy);

        ProcessDefinition definition = definitionWithVariables();
        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-7");
        instance.set("", "customerName", "swapped");
        instance.set("", "creditLimit", Long.valueOf(1));
        instance.execute();

        assertEquals("swapped", queryForString("select NAME from CUSTOMER where ID = 'C-7'"));
    }

    /**
     * A mapped parameter usually carries only the variable name; the type is
     * declared once on the process definition. The task must look the declared
     * type up, otherwise everything is bound as a String - which lenient
     * databases accept but PostgreSQL/Oracle reject on a numeric column.
     */
    public void testNumericParameterIsBoundUsingTheDeclaredVariableType() throws Exception {

        ProcessDefinition definition = definitionWithVariables();
        definition.setProcessVariables(new ProcessVariable[] {
                variable("customerId", String.class),
                variable("customerName", String.class),
                variable("creditLimit", Number.class)   // declared here, not on the parameter
        });

        SQLTask sqlTask = newSqlTask();
        sqlTask.setSqlStmt("insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, ?, ?)");
        sqlTask.setParameters(new ParameterContext[] {
                parameter("ID", "customerId"),
                parameter("NAME", "customerName"),
                parameter("CREDIT_LIMIT", "creditLimit")   // ProcessVariable.forName -> no type
        });

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-N");
        instance.set("", "customerName", "numeric");
        instance.set("", "creditLimit", "1,000");   // typed by the modeler as text
        instance.execute();

        // the thousands separator is only stripped when the numeric coercion ran
        assertEquals("1000", queryForString("select CREDIT_LIMIT from CUSTOMER where ID = 'C-N'"));
    }

    /**
     * The same lookup has to work for the database-mapping strategy, whose
     * mapping elements are plain name references too.
     */
    public void testDatabaseMappingBindsNumericColumnUsingDeclaredType() throws Exception {

        ProcessDefinition definition = definitionWithVariables();
        definition.setProcessVariables(new ProcessVariable[] {
                variable("customerId", String.class),
                variable("customerName", String.class),
                variable("creditLimit", Number.class)
        });

        SQLTask sqlTask = newSqlTask();
        sqlTask.setStrategy(databaseMapping(QueryMode.INSERT));

        ProcessInstance instance = instanceOf(definition, sqlTask);
        instance.set("", "customerId", "C-M");
        instance.set("", "customerName", "mapped numeric");
        instance.set("", "creditLimit", "2,500");
        instance.execute();

        assertEquals("2500", queryForString("select CREDIT_LIMIT from CUSTOMER where ID = 'C-M'"));
    }

    /**
     * The statement the mapping produces for each query mode - key columns
     * become the where clause, the rest the target columns.
     */
    public void testDatabaseMappingGeneratesStatementPerQueryMode() throws Exception {

        ProcessDefinition definition = definitionWithVariables();
        SQLTask sqlTask = newSqlTask();
        ProcessInstance instance = instanceOf(definition, sqlTask);

        assertEquals("select NAME, CREDIT_LIMIT from CUSTOMER where ID = ?",
                databaseMapping(QueryMode.SELECT).prepare(sqlTask, instance).getSql());

        assertEquals("insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, ?, ?)",
                databaseMapping(QueryMode.INSERT).prepare(sqlTask, instance).getSql());

        assertEquals("update CUSTOMER set NAME = ?, CREDIT_LIMIT = ? where ID = ?",
                databaseMapping(QueryMode.UPDATE).prepare(sqlTask, instance).getSql());

        assertEquals("delete from CUSTOMER where ID = ?",
                databaseMapping(QueryMode.DELETE).prepare(sqlTask, instance).getSql());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private SQLTask newSqlTask() {
        SQLTask sqlTask = new SQLTask();
        sqlTask.setTracingTag("sql");
        sqlTask.setConnectionFactory(connectionFactory);

        return sqlTask;
    }

    private DatabaseMappingStrategy databaseMapping(QueryMode queryMode) {
        MappingContext mappingContext = new MappingContext();
        mappingContext.setMappingElements(new MappingElement[] {
                mappingElement("CUSTOMER.ID", "customerId", true),
                mappingElement("CUSTOMER.NAME", "customerName", false),
                mappingElement("CUSTOMER.CREDIT_LIMIT", "creditLimit", false)
        });

        DatabaseMappingStrategy strategy = new DatabaseMappingStrategy();
        strategy.setMappingContext(mappingContext);
        strategy.setQueryMode(queryMode);

        return strategy;
    }

    private ProcessDefinition definitionWithVariables() throws Exception {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setProcessVariables(new ProcessVariable[] {
                variable("customerId", String.class),
                variable("customerName", String.class),
                variable("creditLimit", Long.class)
        });

        return definition;
    }

    private ProcessInstance instanceOf(ProcessDefinition definition, SQLTask sqlTask) throws Exception {
        StartEvent startEvent = new StartEvent();
        startEvent.setTracingTag("start");
        definition.addChildActivity(startEvent);
        definition.addChildActivity(sqlTask);

        SequenceFlow flow = new SequenceFlow();
        flow.setSourceRef("start");
        flow.setTargetRef(sqlTask.getTracingTag());
        definition.addSequenceFlow(flow);

        definition.afterDeserialization();

        return definition.createInstance();
    }

    private static ProcessVariable variable(String name, Class<?> type) {
        ProcessVariable processVariable = ProcessVariable.forName(name);
        processVariable.setType(type);

        return processVariable;
    }

    private static ParameterContext parameter(String columnName, String variableName) {
        ParameterContext parameterContext = new ParameterContext();
        parameterContext.setArgument(textOf(columnName));
        parameterContext.setVariable(ProcessVariable.forName(variableName));

        return parameterContext;
    }

    private static MappingElement mappingElement(String tableAndColumn, String variableName, boolean isKey) {
        MappingElement mappingElement = new MappingElement();
        mappingElement.setArgument(textOf(tableAndColumn));
        mappingElement.setVariable(ProcessVariable.forName(variableName));
        mappingElement.setKey(isKey);

        return mappingElement;
    }

    private static TextContext textOf(String text) {
        TextContext textContext = TextContext.createInstance();
        textContext.setText(text);

        return textContext;
    }

    private static ProcessVariableValue multipleValue(String name, String[] values) {
        ProcessVariableValue processVariableValue = new ProcessVariableValue();
        processVariableValue.setName(name);

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                processVariableValue.moveToAdd();
            }
            processVariableValue.setValue(values[i]);
        }
        processVariableValue.beforeFirst();

        return processVariableValue;
    }

    private static String rootCauseMessage(Throwable throwable) {
        StringBuilder messages = new StringBuilder();

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append(" | ");
            if (current.getCause() == current) {
                break;
            }
        }

        return messages.toString();
    }

    // ------------------------------------------------------------------
    // small JDBC helpers
    // ------------------------------------------------------------------

    private void update(String sql) throws Exception {
        Connection connection = connectionFactory.getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(sql);
            statement.close();
        } finally {
            connectionFactory.releaseConnection(connection);
        }
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
