package org.uengine.kernel.bpmn.sql;

import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ValidationContext;
import org.uengine.kernel.bpmn.SQLTask;

/**
 * The modeler writes the statement. This is the behaviour of the uEngine 3
 * {@code SQLActivity}: {@code sqlStmt} may contain {@code <% %>} expressions
 * (evaluated against the instance) and {@code ?} placeholders bound from
 * {@code parameters}; when {@code query} is on, {@code selectMappings} bind the
 * result columns back into process variables.
 *
 * <p>
 * This is the default strategy of a {@link SQLTask}.
 * </p>
 */
public class DirectSQLStrategy implements SQLTaskStrategy {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    @Override
    public SQLExecutionPlan prepare(SQLTask task, ProcessInstance instance) throws Exception {

        String sql = task.evaluateContent(instance, task.getSqlStmt()).toString().trim();

        return new SQLExecutionPlan(sql, task.getParameters(), task.getSelectMappings(), task.isQuery());
    }

    @Override
    public void validate(SQLTask task, ValidationContext validationContext) {

        String sql = task.getSqlStmt();

        if (sql == null || sql.trim().length() == 0) {
            validationContext.add(task.getName() + " : SQL statement is empty.");
            return;
        }

        int placeholderCount = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                placeholderCount++;
            }
        }

        int parameterCount = task.getParameters() == null ? 0 : task.getParameters().length;

        if (placeholderCount != parameterCount) {
            validationContext.add(task.getName() + " : " + parameterCount
                    + " parameter(s) are mapped but the statement has " + placeholderCount + " '?' placeholder(s).");
        }
    }
}
