package org.uengine.kernel.bpmn;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.uengine.kernel.DefaultActivity;
import org.uengine.kernel.ParameterContext;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ProcessVariable;
import org.uengine.kernel.ProcessVariableValue;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.UEngineException;
import org.uengine.kernel.ValidationContext;
import org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy;
import org.uengine.kernel.bpmn.sql.DirectSQLStrategy;
import org.uengine.kernel.bpmn.sql.SQLExecutionPlan;
import org.uengine.kernel.bpmn.sql.SQLTaskStrategy;
import org.uengine.util.dao.ConnectionFactory;

/**
 * A BPMN task that talks to a database directly.
 *
 * <p>
 * Model it as a {@code <bpmn:serviceTask/>} (or {@code <bpmn:task/>}) whose
 * {@code uengine:properties} JSON carries
 * {@code "_type": "org.uengine.kernel.bpmn.SQLTask"}.
 * </p>
 *
 * <p>
 * How the statement is configured is delegated to a {@link SQLTaskStrategy}:
 * </p>
 * <ul>
 * <li>{@link DirectSQLStrategy} (default) - hand-written {@code sqlStmt},
 * i.e. what the uEngine 3 {@code SQLActivity} did</li>
 * <li>{@link DatabaseMappingStrategy} - {@code TABLE.COLUMN} mapping plus a
 * query mode, i.e. what the uEngine 3 {@code DatabaseMappingActivity} did</li>
 * </ul>
 *
 * <p>
 * Execution, parameter binding, type conversion and result binding are the same
 * for every strategy and live here.
 * </p>
 *
 * @author Jinyoung Jang
 */
public class SQLTask extends DefaultActivity {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    public SQLTask() {
        super("SQL");
        setReplaceWithBlankStringIfNull(true);
    }

    @Override
    protected void executeActivity(ProcessInstance instance) throws Exception {

        SQLExecutionPlan plan = getStrategyOrDefault().prepare(this, instance);

        if (plan.getSql() == null || plan.getSql().trim().length() == 0) {
            throw new UEngineException("SQLTask '" + getName() + "'(" + getTracingTag() + "): no SQL to execute.");
        }

        instance.addDebugInfo("Actual SQL", plan.getSql());

        ConnectionFactory connectionFactory = getConnectionFactory();

        if (connectionFactory == null) {
            throw new UEngineException("SQLTask '" + getName() + "'(" + getTracingTag()
                    + "): no connectionFactory is configured. Set a JDBCConnectionFactory or a DataSourceConnectionFactory.");
        }

        Connection connection = null;
        try {
            try {
                connection = connectionFactory.getConnection();
            } catch (Exception e) {
                throw new UEngineException("SQLTask '" + getName() + "'(" + getTracingTag()
                        + "): failed to get a connection: " + e.getMessage(), e);
            }

            execute(instance, connection, plan);

        } finally {
            if (connection != null) {
                try {
                    connectionFactory.releaseConnection(connection);
                } catch (Exception e) {
                    instance.addDebugInfo("failed to release the SQL connection since: " + e.getMessage());
                }
            }
        }

        fireComplete(instance);
    }

    private void execute(ProcessInstance instance, Connection connection, SQLExecutionPlan plan) throws Exception {

        ParameterContext[] parameters = plan.getParameters();

        ProcessVariableValue[] parameterValues = resolveParameterValues(instance, parameters);

        int rowsToApply = 1;
        for (ProcessVariableValue parameterValue : parameterValues) {
            if (parameterValue != null && parameterValue.size() > rowsToApply) {
                rowsToApply = parameterValue.size();
            }
        }

        if (isApplySingleValueOnly()) {
            rowsToApply = 1;
        }

        PreparedStatement statement = connection.prepareStatement(plan.getSql());
        try {
            for (int row = 0; row < rowsToApply; row++) {

                bindParameters(instance, statement, parameters, parameterValues, row);

                if (plan.isQuery()) {
                    ResultSet resultSet = statement.executeQuery();
                    try {
                        bindResultSet(instance, resultSet, plan.getSelectMappings());
                    } finally {
                        resultSet.close();
                    }
                } else {
                    int affected = statement.executeUpdate();
                    instance.addDebugInfo("Affected Rows", affected);
                }
            }
        } finally {
            statement.close();
        }
    }

    /**
     * Reads every mapped parameter once, up front - a parameter may hold
     * multiple values (a "multiple" process variable), in which case the
     * statement is executed once per value.
     */
    private ProcessVariableValue[] resolveParameterValues(ProcessInstance instance, ParameterContext[] parameters)
            throws Exception {

        ProcessVariableValue[] parameterValues = new ProcessVariableValue[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            ParameterContext parameter = parameters[i];

            if (parameter == null) {
                continue;
            }

            if (parameter.getTransformerMapping() != null
                    && parameter.getTransformerMapping().getTransformer() != null) {

                Object transformed = parameter.getTransformerMapping().getTransformer()
                        .letTransform(instance, parameter.getTransformerMapping().getLinkedArgumentName());

                if (transformed instanceof ProcessVariableValue) {
                    parameterValues[i] = (ProcessVariableValue) transformed;
                } else {
                    parameterValues[i] = new ProcessVariableValue();
                    parameterValues[i].setValue((Serializable) transformed);
                }

            } else if (parameter.getVariable() == null) {
                parameterValues[i] = new ProcessVariableValue();

            } else if (parameter.getVariable().getName() != null
                    && parameter.getVariable().getName().startsWith("[")) {
                // bean expression, e.g. [Arguments].customer.name
                parameterValues[i] = new ProcessVariableValue();
                parameterValues[i]
                        .setValue((Serializable) instance.getBeanProperty(parameter.getVariable().getName()));

            } else {
                parameterValues[i] = parameter.getVariable().getMultiple(instance, "");
            }
        }

        return parameterValues;
    }

    private void bindParameters(ProcessInstance instance, PreparedStatement statement, ParameterContext[] parameters,
            ProcessVariableValue[] parameterValues, int row) throws Exception {

        for (int i = 0; i < parameters.length; i++) {

            Object value = null;
            try {
                ProcessVariableValue parameterValue = parameterValues[i];

                if (parameterValue != null && parameterValue.size() > 0) {
                    parameterValue.setCursor(Math.min(row, parameterValue.size() - 1));
                    value = parameterValue.getValue(instance);
                    parameterValue.beforeFirst();
                }

                instance.addDebugInfo((i + 1) + "th parameter (column: "
                        + (parameters[i] != null && parameters[i].getArgument() != null
                                ? parameters[i].getArgument().getText()
                                : "?")
                        + ")", value);

                if (value instanceof Calendar) {
                    statement.setTimestamp(i + 1, new Timestamp(((Calendar) value).getTimeInMillis()));
                    continue;
                }

                if (value instanceof Date) {
                    statement.setTimestamp(i + 1, new Timestamp(((Date) value).getTime()));
                    continue;
                }

                if (value instanceof RoleMapping) {
                    value = ((RoleMapping) value).getEndpoint();
                } else if (value instanceof String) {
                    value = ((String) value).trim();
                } else if (value == null && isReplaceWithBlankStringIfNull()) {
                    value = "";
                }

                Class<?> declaredType = declaredTypeOf(parameters[i]);

                if (Number.class.equals(declaredType) || Long.class.equals(declaredType)
                        || Integer.class.equals(declaredType)) {
                    value = toNumber(value);
                } else if (Boolean.class.equals(declaredType) && !(value instanceof Boolean)) {
                    value = Boolean.valueOf(String.valueOf(value));
                }

                statement.setObject(i + 1, value);

            } catch (Exception e) {
                throw new UEngineException("SQLTask '" + getName() + "'(" + getTracingTag() + "): failed to bind ["
                        + value + "] as the " + (i + 1) + "th parameter: " + e.getClass().getName() + ": "
                        + e.getMessage(), e);
            }
        }
    }

    /**
     * The declared type of a mapped parameter.
     *
     * <p>
     * A {@code ParameterContext} usually only carries the variable <em>name</em>
     * ({@code {"variable": {"name": "creditLimit"}}}) - the type is declared once
     * on the process definition. Without this lookup every value would be bound
     * as a String, which lenient databases accept but strict ones (PostgreSQL,
     * Oracle) reject with "column is of type bigint but expression is of type
     * character varying".
     * </p>
     */
    private Class<?> declaredTypeOf(ParameterContext parameter) {

        if (parameter == null || parameter.getVariable() == null) {
            return null;
        }

        Class<?> type = parameter.getVariable().getType();
        if (type != null) {
            return type;
        }

        String variableName = parameter.getVariable().getName();
        if (variableName == null || variableName.startsWith("[")) {
            return null;
        }

        try {
            if (getProcessDefinition() == null) {
                return null;
            }

            ProcessVariable declared = getProcessDefinition().getProcessVariable(variableName);

            return declared != null ? declared.getType() : null;
        } catch (Exception e) {
            return null; // undeclared variable - bind as-is
        }
    }

    private static Object toNumber(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim().replace(",", "");

        if (text.isEmpty()) {
            return null;
        }

        try {
            return Long.valueOf(text);
        } catch (NumberFormatException notALong) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException notADouble) {
                return Long.valueOf(0);
            }
        }
    }

    /**
     * Binds the selected columns back into process variables. When more than one
     * row comes back (and applySingleValueOnly is off) each mapped variable
     * becomes a multiple-valued variable.
     */
    private void bindResultSet(ProcessInstance instance, ResultSet resultSet, ParameterContext[] selectMappings)
            throws Exception {

        if (selectMappings == null || selectMappings.length == 0) {
            instance.addDebugInfo("Result Set", "no select mapping is configured - the result is discarded");
            return;
        }

        Map<String, ProcessVariableValue> boundValues = new LinkedHashMap<String, ProcessVariableValue>();
        Set<String> variableOrder = new LinkedHashSet<String>();

        int rowCount = 0;
        while (resultSet.next()) {
            rowCount++;

            ResultSetMetaData metaData = resultSet.getMetaData();

            for (int column = 1; column <= metaData.getColumnCount(); column++) {
                String columnLabel = metaData.getColumnLabel(column);

                for (ParameterContext selectMapping : selectMappings) {

                    if (selectMapping == null || selectMapping.getArgument() == null
                            || selectMapping.getArgument().getText() == null || selectMapping.getVariable() == null) {
                        continue;
                    }

                    if (!columnLabel.equalsIgnoreCase(selectMapping.getArgument().getText())) {
                        continue;
                    }

                    String variableName = selectMapping.getVariable().getName();
                    Serializable value = readColumn(instance, variableName, resultSet, column);

                    ProcessVariableValue variableValue = boundValues.get(variableName);
                    if (variableValue == null) {
                        variableValue = new ProcessVariableValue();
                        boundValues.put(variableName, variableValue);
                        variableOrder.add(variableName);
                    } else {
                        variableValue.moveToAdd();
                    }
                    variableValue.setValue(value);

                    instance.addDebugInfo("  bind " + columnLabel + " -> " + variableName, value);
                    break;
                }
            }

            if (isApplySingleValueOnly()) {
                break;
            }
        }

        if (rowCount == 0) {
            instance.addDebugInfo("Result Set", "empty");
            return;
        }

        for (String variableName : variableOrder) {
            ProcessVariableValue variableValue = boundValues.get(variableName);
            variableValue.beforeFirst();

            if (variableValue.size() == 1) {
                instance.set("", variableName, variableValue.getValue());
            } else {
                variableValue.setName(variableName);
                instance.set("", variableName, variableValue);
            }
        }
    }

    /**
     * Reads a column with the JDBC getter matching the declared type of the
     * target process variable; falls back to {@code getObject} when the type is
     * unknown.
     */
    protected Serializable readColumn(ProcessInstance instance, String variableName, ResultSet resultSet, int column)
            throws Exception {

        Class<?> type = null;
        try {
            if (getProcessDefinition() != null && getProcessDefinition().getProcessVariable(variableName) != null) {
                type = getProcessDefinition().getProcessVariable(variableName).getType();
            }
        } catch (Exception e) {
            // undeclared variable - fall back to the raw value below
        }

        if (String.class.equals(type)) {
            String value = resultSet.getString(column);
            return value == null && isReplaceWithBlankStringIfNull() ? "" : value;
        }
        if (Integer.class.equals(type)) {
            return Integer.valueOf(resultSet.getInt(column));
        }
        if (Long.class.equals(type)) {
            return Long.valueOf(resultSet.getLong(column));
        }
        if (Boolean.class.equals(type)) {
            return Boolean.valueOf(resultSet.getBoolean(column));
        }
        if (Number.class.equals(type)) {
            return (Serializable) resultSet.getObject(column);
        }
        if (Date.class.equals(type)) {
            return resultSet.getTimestamp(column);
        }
        if (Calendar.class.equals(type)) {
            Date date = resultSet.getTimestamp(column);
            Calendar calendar = Calendar.getInstance();
            if (date != null) {
                calendar.setTime(date);
            }
            return calendar;
        }

        return (Serializable) resultSet.getObject(column);
    }

    @Override
    public Map getActivityDetails(ProcessInstance instance, String locale) throws Exception {
        Map details = super.getActivityDetails(instance, locale);

        try {
            details.put("query string", getStrategyOrDefault().prepare(this, instance).getSql());
        } catch (Exception e) {
            details.put("query string", "(not resolvable: " + e.getMessage() + ")");
        }

        return details;
    }

    @Override
    public ValidationContext validate(Map options) {
        ValidationContext validationContext = super.validate(options);

        if (getConnectionFactory() == null) {
            validationContext.add(getName() + " : no connectionFactory is configured.");
        }

        getStrategyOrDefault().validate(this, validationContext);

        return validationContext;
    }

    public SQLTaskStrategy getStrategyOrDefault() {
        return strategy != null ? strategy : new DirectSQLStrategy();
    }

    SQLTaskStrategy strategy;

    public SQLTaskStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(SQLTaskStrategy strategy) {
        this.strategy = strategy;
    }

    ConnectionFactory connectionFactory;

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    String sqlStmt;

    public String getSqlStmt() {
        return sqlStmt;
    }

    public void setSqlStmt(String sqlStmt) {
        this.sqlStmt = sqlStmt;
    }

    ParameterContext[] parameters = new ParameterContext[0];

    public ParameterContext[] getParameters() {
        return parameters;
    }

    public void setParameters(ParameterContext[] parameters) {
        this.parameters = parameters == null ? new ParameterContext[0] : parameters;
    }

    ParameterContext[] selectMappings = new ParameterContext[0];

    public ParameterContext[] getSelectMappings() {
        return selectMappings;
    }

    public void setSelectMappings(ParameterContext[] selectMappings) {
        this.selectMappings = selectMappings == null ? new ParameterContext[0] : selectMappings;
    }

    boolean query;

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean query) {
        this.query = query;
    }

    boolean applySingleValueOnly;

    public boolean isApplySingleValueOnly() {
        return applySingleValueOnly;
    }

    public void setApplySingleValueOnly(boolean applySingleValueOnly) {
        this.applySingleValueOnly = applySingleValueOnly;
    }

    boolean replaceWithBlankStringIfNull;

    public boolean isReplaceWithBlankStringIfNull() {
        return replaceWithBlankStringIfNull;
    }

    public void setReplaceWithBlankStringIfNull(boolean replaceWithBlankStringIfNull) {
        this.replaceWithBlankStringIfNull = replaceWithBlankStringIfNull;
    }
}
