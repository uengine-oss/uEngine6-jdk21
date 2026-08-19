package org.uengine.util;

import java.util.ArrayList;
import java.util.List;

import org.uengine.contexts.MappingContext;
import org.uengine.contexts.TextContext;
import org.uengine.kernel.MappingElement;
import org.uengine.kernel.ParameterContext;
import org.uengine.kernel.bpmn.sql.QueryMode;

/**
 * Generates a SQL statement out of a {@link MappingContext} whose arguments are
 * written as {@code TABLE.COLUMN} and whose variables are the process
 * variables to read from / write into.
 *
 * <p>
 * Revived from the uEngine 3 {@code SQLGeneratorForDBMappingActivity}, which
 * backed the old {@code DatabaseMappingActivity}. Behaviour differences worth
 * knowing:
 * </p>
 * <ul>
 * <li>DELETE now only binds the key columns (the old version also bound the
 * non-key columns even though they never appeared in the statement)</li>
 * <li>the generated parameter array is trimmed, so it always matches the number
 * of {@code ?} placeholders</li>
 * </ul>
 *
 * @author Jinyoung Jang
 */
public class SQLGeneratorForDatabaseMapping {

    private final MappingContext mappingContext;

    private ParameterContext[] parameters = new ParameterContext[0];
    private ParameterContext[] selectMappings = new ParameterContext[0];

    public SQLGeneratorForDatabaseMapping(MappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    public String getGeneratedSql(QueryMode queryMode) {

        if (mappingContext == null || mappingContext.getMappingElements() == null
                || mappingContext.getMappingElements().length == 0) {
            throw new IllegalStateException(
                    "DatabaseMapping: no mapping element is configured. Map at least one 'TABLE.COLUMN' argument.");
        }

        String tableName = null;
        StringBuilder columnClause = new StringBuilder();
        StringBuilder valuesClause = new StringBuilder();
        StringBuilder whereClause = new StringBuilder();

        List<ParameterContext> valueParameters = new ArrayList<ParameterContext>();
        List<ParameterContext> keyParameters = new ArrayList<ParameterContext>();
        List<ParameterContext> selectMappingList = new ArrayList<ParameterContext>();

        String columnSeparator = "";
        String whereSeparator = "";

        for (MappingElement mappingElement : mappingContext.getMappingElements()) {

            if (mappingElement == null || mappingElement.getArgument() == null
                    || mappingElement.getArgument().getText() == null) {
                continue;
            }

            String[] tableAndColumn = splitTableAndColumn(mappingElement.getArgument().getText());
            tableName = tableAndColumn[0];
            String columnName = tableAndColumn[1];

            boolean isKey = mappingElement.isKey();

            if (isKey && queryMode != QueryMode.INSERT) {
                whereClause.append(whereSeparator).append(columnName).append(" = ?");
                whereSeparator = " and ";
                keyParameters.add(mappingElement);
                continue;
            }

            switch (queryMode) {
                case INSERT:
                    columnClause.append(columnSeparator).append(columnName);
                    valuesClause.append(columnSeparator).append("?");
                    columnSeparator = ", ";
                    valueParameters.add(mappingElement);
                    break;

                case UPDATE:
                    columnClause.append(columnSeparator).append(columnName).append(" = ?");
                    columnSeparator = ", ";
                    valueParameters.add(mappingElement);
                    break;

                case SELECT:
                    columnClause.append(columnSeparator).append(columnName);
                    columnSeparator = ", ";
                    selectMappingList.add(toSelectMapping(columnName, mappingElement));
                    break;

                case DELETE:
                    // only key columns take part in a delete
                    break;
            }
        }

        // the values of the where clause are bound after the ones of the set/values clause
        List<ParameterContext> allParameters = new ArrayList<ParameterContext>(valueParameters);
        allParameters.addAll(keyParameters);

        this.parameters = allParameters.toArray(new ParameterContext[0]);
        this.selectMappings = selectMappingList.toArray(new ParameterContext[0]);

        String sql;
        switch (queryMode) {
            case INSERT:
                sql = "insert into " + tableName + " (" + columnClause + ") values (" + valuesClause + ")";
                break;
            case UPDATE:
                sql = "update " + tableName + " set " + columnClause;
                break;
            case SELECT:
                sql = "select " + columnClause + " from " + tableName;
                break;
            case DELETE:
                sql = "delete from " + tableName;
                break;
            default:
                throw new IllegalArgumentException("Unsupported query mode: " + queryMode);
        }

        if (whereClause.length() > 0) {
            sql = sql + " where " + whereClause;
        }

        return sql;
    }

    private static ParameterContext toSelectMapping(String columnName, MappingElement mappingElement) {
        ParameterContext selectMapping = new ParameterContext();

        TextContext argument = TextContext.createInstance();
        argument.setText(columnName);

        selectMapping.setArgument(argument);
        selectMapping.setVariable(mappingElement.getVariable());

        return selectMapping;
    }

    private static String[] splitTableAndColumn(String argumentText) {
        int separator = argumentText.lastIndexOf('.');

        if (separator < 1 || separator == argumentText.length() - 1) {
            throw new IllegalStateException(
                    "DatabaseMapping: argument '" + argumentText + "' must be written as 'TABLE.COLUMN'.");
        }

        return new String[] { argumentText.substring(0, separator).trim(),
                argumentText.substring(separator + 1).trim() };
    }

    public ParameterContext[] getParameters() {
        return parameters;
    }

    public ParameterContext[] getSelectMappings() {
        return selectMappings;
    }
}
