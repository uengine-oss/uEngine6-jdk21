package org.uengine.kernel.bpmn.sql;

import java.io.Serializable;

import org.uengine.kernel.ParameterContext;

/**
 * What a {@link SQLTaskStrategy} hands back to
 * {@link org.uengine.kernel.bpmn.SQLTask}: a ready-to-prepare statement plus the
 * binding information for it.
 *
 * <p>
 * Keeping the JDBC work in the task (and only the "which SQL and which
 * bindings" decision in the strategy) means every configuration style shares
 * exactly the same execution, type conversion and result binding semantics.
 * </p>
 */
public class SQLExecutionPlan implements Serializable {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    private String sql;
    private ParameterContext[] parameters = new ParameterContext[0];
    private ParameterContext[] selectMappings = new ParameterContext[0];
    private boolean query;

    public SQLExecutionPlan() {
    }

    public SQLExecutionPlan(String sql, ParameterContext[] parameters, ParameterContext[] selectMappings,
            boolean query) {
        setSql(sql);
        setParameters(parameters);
        setSelectMappings(selectMappings);
        setQuery(query);
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public ParameterContext[] getParameters() {
        return parameters;
    }

    public void setParameters(ParameterContext[] parameters) {
        this.parameters = parameters == null ? new ParameterContext[0] : parameters;
    }

    public ParameterContext[] getSelectMappings() {
        return selectMappings;
    }

    public void setSelectMappings(ParameterContext[] selectMappings) {
        this.selectMappings = selectMappings == null ? new ParameterContext[0] : selectMappings;
    }

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean query) {
        this.query = query;
    }
}
