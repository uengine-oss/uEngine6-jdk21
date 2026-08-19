package org.uengine.kernel.bpmn.sql;

import org.uengine.contexts.MappingContext;
import org.uengine.kernel.MappingElement;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ValidationContext;
import org.uengine.kernel.bpmn.SQLTask;
import org.uengine.util.SQLGeneratorForDatabaseMapping;

/**
 * No hand-written SQL: the modeler maps process variables onto
 * {@code TABLE.COLUMN} arguments, flags which of them are keys and picks a
 * {@link QueryMode}. The statement is generated from that mapping.
 *
 * <p>
 * This revives the uEngine 3 {@code DatabaseMappingActivity}. Back then it was a
 * separate activity type that internally instantiated a {@code SQLActivity};
 * here it is just another configuration style of the one {@link SQLTask}, so
 * both styles share connection handling, parameter binding and result mapping.
 * </p>
 *
 * <p>
 * Example {@code uengine:properties} JSON:
 * </p>
 *
 * <pre>
 * {
 *   "_type": "org.uengine.kernel.bpmn.SQLTask",
 *   "connectionFactory": { "_type": "org.uengine.util.dao.DataSourceConnectionFactory" },
 *   "strategy": {
 *     "_type": "org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy",
 *     "queryMode": "INSERT",
 *     "mappingContext": {
 *       "mappingElements": [
 *         {"argument": {"text": "CUSTOMER.ID"},   "variable": {"name": "customerId"}, "isKey": true},
 *         {"argument": {"text": "CUSTOMER.NAME"}, "variable": {"name": "customerName"}}
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
public class DatabaseMappingStrategy implements SQLTaskStrategy {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    MappingContext mappingContext;
    QueryMode queryMode = QueryMode.INSERT;

    @Override
    public SQLExecutionPlan prepare(SQLTask task, ProcessInstance instance) throws Exception {

        SQLGeneratorForDatabaseMapping generator = new SQLGeneratorForDatabaseMapping(getMappingContext());

        String sql = generator.getGeneratedSql(getQueryMode());

        instance.addDebugInfo("Generated SQL (DatabaseMapping/" + getQueryMode() + ")", sql);

        return new SQLExecutionPlan(sql, generator.getParameters(), generator.getSelectMappings(),
                getQueryMode() == QueryMode.SELECT);
    }

    @Override
    public void validate(SQLTask task, ValidationContext validationContext) {

        if (getMappingContext() == null || getMappingContext().getMappingElements() == null
                || getMappingContext().getMappingElements().length == 0) {
            validationContext.add(task.getName() + " : no column is mapped for the database mapping.");
            return;
        }

        boolean hasKey = false;
        for (MappingElement mappingElement : getMappingContext().getMappingElements()) {
            if (mappingElement == null) {
                continue;
            }

            if (mappingElement.getArgument() == null || mappingElement.getArgument().getText() == null
                    || mappingElement.getArgument().getText().lastIndexOf('.') < 1) {
                validationContext.add(task.getName() + " : mapped argument '"
                        + (mappingElement.getArgument() == null ? null : mappingElement.getArgument().getText())
                        + "' must be written as 'TABLE.COLUMN'.");
            }

            if (mappingElement.isKey()) {
                hasKey = true;
            }
        }

        if (!hasKey && (getQueryMode() == QueryMode.UPDATE || getQueryMode() == QueryMode.DELETE)) {
            validationContext.add(task.getName() + " : " + getQueryMode()
                    + " without any key column would affect every row of the table.");
        }
    }

    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public void setMappingContext(MappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    public QueryMode getQueryMode() {
        return queryMode;
    }

    public void setQueryMode(QueryMode queryMode) {
        this.queryMode = queryMode;
    }
}
