/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.plugin.snowflake;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public class TestingSnowflakeServer
{
    public static final String TEST_URL = requireNonNull(System.getProperty("snowflake.test.server.url"), "snowflake.test.server.url is not set");
    public static final String TEST_USER = requireNonNull(System.getProperty("snowflake.test.server.user"), "snowflake.test.server.user is not set");
    public static final String TEST_PASSWORD = requireNonNull(System.getProperty("snowflake.test.server.password"), "snowflake.test.server.password is not set");
    public static final String TEST_DATABASE = requireNonNull(System.getProperty("snowflake.test.server.database"), "snowflake.test.server.database is not set");
    public static final String TEST_ROLE = requireNonNull(System.getProperty("snowflake.test.server.role"), "snowflake.test.server.role is not set");
    public static final String TEST_SCHEMA = requireNonNull(System.getProperty("snowflake.test.server.schema"), "snowflake.test.server.schema is not set");

    private TestingSnowflakeServer() {}

    public static void execute(String sql)
    {
        execute(TEST_URL, getProperties(), sql);
    }

    static void execute(String url, Properties properties, String sql)
    {
        try (Connection connection = DriverManager.getConnection(url, properties);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        catch (SQLException e) {
            e.printStackTrace(System.err);
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            throw new RuntimeException(e);
        }
    }

    static void executeUpdate(String url, Properties properties, String sql)
    {
        try (Connection connection = DriverManager.getConnection(url, properties);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        catch (SQLException e) {
            e.printStackTrace(System.err);
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            throw new RuntimeException(e);
        }
    }

    static int executeAndGetCount(String url, Properties properties, String query)
    {
        try (Connection connection = DriverManager.getConnection(url, properties);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    static Properties getProperties()
    {
        Properties properties = new Properties();
        properties.setProperty("user", TEST_USER);
        properties.setProperty("password", TEST_PASSWORD);
        properties.setProperty("db", TEST_DATABASE);
        properties.setProperty("schema", TEST_SCHEMA);
        properties.setProperty("role", TEST_ROLE);
        properties.setProperty("schema", TEST_SCHEMA);
        return properties;
    }
}
