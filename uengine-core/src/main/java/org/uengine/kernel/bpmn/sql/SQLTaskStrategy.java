package org.uengine.kernel.bpmn.sql;

import java.io.Serializable;

import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ValidationContext;
import org.uengine.kernel.bpmn.SQLTask;

/**
 * The way a {@link SQLTask} is configured. One task type, several configuration
 * styles:
 *
 * <ul>
 * <li>{@link DirectSQLStrategy} - the modeler writes the SQL (the old
 * {@code SQLActivity})</li>
 * <li>{@link DatabaseMappingStrategy} - the modeler maps process variables onto
 * {@code TABLE.COLUMN} and picks a query mode; the SQL is generated (the old
 * {@code DatabaseMappingActivity})</li>
 * </ul>
 */
public interface SQLTaskStrategy extends Serializable {

    /**
     * Produces the statement to run for this instance. Called once per
     * execution, so it may read process variables.
     */
    SQLExecutionPlan prepare(SQLTask task, ProcessInstance instance) throws Exception;

    /**
     * Design time check, surfaced through {@link SQLTask#validate(java.util.Map)}.
     */
    default void validate(SQLTask task, ValidationContext validationContext) {
    }
}
