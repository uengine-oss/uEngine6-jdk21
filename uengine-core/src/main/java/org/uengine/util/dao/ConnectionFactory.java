package org.uengine.util.dao;

import java.io.Serializable;
import java.sql.Connection;

/**
 * Abstraction over 'where the JDBC connection comes from' for the SQL-executing
 * activities ({@link org.uengine.kernel.bpmn.SQLTask}).
 *
 * <p>
 * Implementations are part of the process definition, therefore they must be
 * serializable and must be describable purely by JavaBean properties so that
 * they can travel through the {@code uengine:properties} JSON of a BPMN file.
 * </p>
 *
 * @author Jinyoung Jang
 */
public interface ConnectionFactory extends Serializable {

    Connection getConnection() throws Exception;

    /**
     * Gives the factory a chance to decide how the connection is returned.
     * DataSource based factories may hand the connection back to a Spring
     * managed transaction instead of physically closing it.
     */
    default void releaseConnection(Connection connection) throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * When true, the SQLTask must not commit/rollback by itself - the
     * surrounding (Spring) transaction owns the connection.
     */
    default boolean isTransactionManagedExternally() {
        return false;
    }
}
