package org.uengine.util.dao;

import java.sql.Connection;
import java.util.Map;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.uengine.kernel.GlobalContext;

/**
 * Resolves the connection from a DataSource that already exists in the runtime.
 *
 * <p>
 * Resolution order:
 * </p>
 * <ol>
 * <li>{@code dataSourceName} matched against the Spring bean names of every
 * {@link DataSource} bean</li>
 * <li>{@code dataSourceName} looked up through JNDI (legacy/WAS deployments)</li>
 * <li>when {@code dataSourceName} is empty, the single/primary DataSource bean
 * of the application (i.e. the uEngine repository itself)</li>
 * </ol>
 *
 * <p>
 * Connections are borrowed through {@link DataSourceUtils} so that a SQLTask
 * running inside a Spring managed transaction joins that transaction instead of
 * opening a second one.
 * </p>
 *
 * @author Jinyoung Jang
 */
public class DataSourceConnectionFactory implements ConnectionFactory {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    String dataSourceName;

    public DataSourceConnectionFactory() {
    }

    public DataSourceConnectionFactory(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    @Override
    public Connection getConnection() throws Exception {
        return DataSourceUtils.getConnection(resolveDataSource());
    }

    @Override
    public void releaseConnection(Connection connection) throws Exception {
        DataSourceUtils.releaseConnection(connection, resolveDataSource());
    }

    @Override
    public boolean isTransactionManagedExternally() {
        return true;
    }

    public DataSource resolveDataSource() throws Exception {
        String name = dataSourceName != null ? dataSourceName.trim() : "";

        if (name.length() > 0) {
            Map<String, DataSource> dataSources = null;
            try {
                dataSources = GlobalContext.getComponents(DataSource.class);
            } catch (Exception e) {
                // no spring context (e.g. unit test) - fall through to JNDI
            }

            if (dataSources != null && dataSources.containsKey(name)) {
                return dataSources.get(name);
            }

            try {
                Object found = new InitialContext().lookup(name);
                if (found instanceof DataSource) {
                    return (DataSource) found;
                }
            } catch (Exception e) {
                // fall through to the error below
            }

            throw new IllegalStateException(
                    "DataSourceConnectionFactory: no DataSource named '" + name + "' found in the Spring context or JNDI."
                            + (dataSources != null ? " Available: " + dataSources.keySet() : ""));
        }

        DataSource dataSource = GlobalContext.getComponent(DataSource.class);

        if (dataSource == null) {
            throw new IllegalStateException("DataSourceConnectionFactory: no default DataSource available.");
        }

        return dataSource;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }
}
