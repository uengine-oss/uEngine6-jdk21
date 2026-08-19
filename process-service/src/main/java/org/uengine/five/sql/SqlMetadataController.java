package org.uengine.five.sql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.util.dao.ConnectionFactory;
import org.uengine.util.dao.DataSourceConnectionFactory;
import org.uengine.util.dao.JDBCConnectionFactory;

/**
 * Table / column metadata for modeling a {@link org.uengine.kernel.bpmn.SQLTask}.
 *
 * <p>
 * The uEngine 3 DatabaseMappingActivity editor had a "Tables" picker and a
 * "refresh" button that filled the right-hand mapping tree with the real
 * columns of the chosen table. This endpoint is that, for the uEngine 6
 * modeler: the panel asks for the tables of the configured connection and then
 * for the columns of the selected table, and feeds them to the mapper as the
 * target tree.
 * </p>
 *
 * <p>
 * Read-only: it only reads {@link DatabaseMetaData}; it never runs modeler
 * supplied SQL.
 * </p>
 */
@RestController
public class SqlMetadataController {

    static final int MAX_TABLES = 500;

    @GetMapping("/sql-metadata/tables")
    public Map<String, Object> tables(
            @RequestParam(value = "dataSourceName", required = false) String dataSourceName,
            @RequestParam(value = "driverClass", required = false) String driverClass,
            @RequestParam(value = "connectionString", required = false) String connectionString,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "schema", required = false) String schema) {

        ConnectionFactory connectionFactory =
                connectionFactoryFor(dataSourceName, driverClass, connectionString, userId, password);

        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();

            List<String> names = new ArrayList<String>();
            ResultSet resultSet = metaData.getTables(connection.getCatalog(), schema, "%",
                    new String[] { "TABLE", "VIEW" });
            try {
                while (resultSet.next() && names.size() < MAX_TABLES) {
                    names.add(resultSet.getString("TABLE_NAME"));
                }
            } finally {
                resultSet.close();
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("tables", names);

            return result;

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "failed to read table list: " + e.getMessage(), e);
        } finally {
            release(connectionFactory, connection);
        }
    }

    @GetMapping("/sql-metadata/columns")
    public Map<String, Object> columns(
            @RequestParam("table") String table,
            @RequestParam(value = "dataSourceName", required = false) String dataSourceName,
            @RequestParam(value = "driverClass", required = false) String driverClass,
            @RequestParam(value = "connectionString", required = false) String connectionString,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "schema", required = false) String schema) {

        ConnectionFactory connectionFactory =
                connectionFactoryFor(dataSourceName, driverClass, connectionString, userId, password);

        Connection connection = null;
        try {
            connection = connectionFactory.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            // the JDBC metadata calls are case sensitive; try the name as given and upper cased
            String resolvedTable = resolveTableName(metaData, catalog, schema, table);

            Set<String> primaryKeys = new HashSet<String>();
            ResultSet keys = metaData.getPrimaryKeys(catalog, schema, resolvedTable);
            try {
                while (keys.next()) {
                    primaryKeys.add(keys.getString("COLUMN_NAME"));
                }
            } finally {
                keys.close();
            }

            List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
            ResultSet resultSet = metaData.getColumns(catalog, schema, resolvedTable, "%");
            try {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    Map<String, Object> column = new LinkedHashMap<String, Object>();
                    column.put("name", columnName);
                    column.put("type", resultSet.getString("TYPE_NAME"));
                    column.put("nullable", resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                    column.put("primaryKey", primaryKeys.contains(columnName));
                    columns.add(column);
                }
            } finally {
                resultSet.close();
            }

            if (columns.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such table: " + table);
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("table", resolvedTable);
            result.put("columns", columns);

            return result;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "failed to read columns of " + table + ": " + e.getMessage(), e);
        } finally {
            release(connectionFactory, connection);
        }
    }

    // ------------------------------------------------------------------

    private static String resolveTableName(DatabaseMetaData metaData, String catalog, String schema, String table)
            throws Exception {

        for (String candidate : new String[] { table, table.toUpperCase(), table.toLowerCase() }) {
            ResultSet resultSet = metaData.getTables(catalog, schema, candidate, null);
            try {
                if (resultSet.next()) {
                    return resultSet.getString("TABLE_NAME");
                }
            } finally {
                resultSet.close();
            }
        }

        return table;
    }

    private static ConnectionFactory connectionFactoryFor(String dataSourceName, String driverClass,
            String connectionString, String userId, String password) {

        if (connectionString != null && connectionString.trim().length() > 0) {
            return new JDBCConnectionFactory(driverClass, connectionString, userId, password);
        }

        return new DataSourceConnectionFactory(dataSourceName);
    }

    private static void release(ConnectionFactory connectionFactory, Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connectionFactory.releaseConnection(connection);
        } catch (Exception e) {
            // nothing useful to do at this point
        }
    }
}
