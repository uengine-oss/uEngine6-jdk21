package org.uengine.util.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import org.uengine.kernel.GlobalContext;

/**
 * Plain JDBC connection factory. Everything (driver, url, credentials) is
 * configured at modeling time on the task itself.
 *
 * <p>
 * Every property value goes through {@link #resolve(String)} so that a modeler
 * can write {@code ${jdbc.crm.url}} and let the server-side configuration
 * (uengine.properties / spring properties bridged into GlobalContext) supply the
 * real value - passwords then never need to live inside a BPMN file.
 * </p>
 *
 * @author Jinyoung Jang
 */
public class JDBCConnectionFactory implements ConnectionFactory {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    String driverClass;
    String connectionString;
    String userId;
    String password;

    public JDBCConnectionFactory() {
    }

    public JDBCConnectionFactory(String driverClass, String connectionString, String userId, String password) {
        this.driverClass = driverClass;
        this.connectionString = connectionString;
        this.userId = userId;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws Exception {
        String url = resolve(getConnectionString());

        if (url == null || url.trim().length() == 0) {
            throw new IllegalStateException(
                    "JDBCConnectionFactory: 'connectionString' (JDBC URL) is not configured.");
        }

        String driver = resolve(getDriverClass());
        if (driver != null && driver.trim().length() > 0) {
            Class.forName(driver.trim());
        }

        String user = resolve(getUserId());
        if (user == null) {
            return DriverManager.getConnection(url);
        }

        Properties info = new Properties();
        info.put("user", user);
        info.put("password", resolve(getPassword()) == null ? "" : resolve(getPassword()));

        return DriverManager.getConnection(url, info);
    }

    /**
     * Replaces {@code ${key}} occurrences with the server side configuration
     * value of {@code key}. Unknown keys are left untouched so that the failure
     * message still shows what was expected.
     */
    static String resolve(String value) {
        if (value == null) {
            return null;
        }

        int start = value.indexOf("${");
        if (start < 0) {
            return value;
        }

        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        while (start >= 0) {
            int end = value.indexOf('}', start);
            if (end < 0) {
                break;
            }

            String key = value.substring(start + 2, end);
            String replacement = GlobalContext.getPropertyString(key);

            resolved.append(value, cursor, start);
            resolved.append(replacement != null ? replacement : value.substring(start, end + 1));

            cursor = end + 1;
            start = value.indexOf("${", cursor);
        }
        resolved.append(value.substring(cursor));

        return resolved.toString();
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
