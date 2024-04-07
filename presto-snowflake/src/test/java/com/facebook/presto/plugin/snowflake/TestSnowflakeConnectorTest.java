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

import org.testng.annotations.Test;

import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.TEST_URL;
import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.dropTable;
import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.dropTableIfExists;
import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.execute;
import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.executeAndGetCount;
import static com.facebook.presto.plugin.snowflake.TestingSnowflakeServer.getProperties;

@Test
public class TestSnowflakeConnectorTest
{
    @Test
    protected void createTableWithDefaultColumns()
    {
        String url = TEST_URL;
        String tableName = "test_table_create_table_with_default_columns";
        dropTableIfExists(url, tableName);
        String createTableWithDefaultColumnSql = "CREATE TABLE " + tableName + " IF NOT EXISTS ( col_required BIGINT NOT NULL, col_nullable BIGINT, " +
                "col_default BIGINT DEFAULT 43, " +
                "col_nonnull_default BIGINT NOT NULL DEFAULT 42," +
                "col_required2 BIGINT NOT NULL )";
        execute(url, getProperties(), createTableWithDefaultColumnSql);
        dropTable(url, tableName);
    }

    @Test
    public void testInsert()
    {
        String url = TEST_URL;
        String tableName = "test_table_insert";
        dropTableIfExists(url, tableName);
        String createTableSQL = "CREATE TABLE " + tableName + " AS SELECT 123 x";
        execute(url, getProperties(), createTableSQL);
        String insertSQL = "INSERT INTO " + tableName + " (x) VALUES (456)";
        execute(url, getProperties(), insertSQL);
        dropTable(url, tableName);
    }

    public void testAlterTableRenameColumn()
    {
        String url = TEST_URL;
        String tableName = "test_table_rename_column";
        String validTargetColumnName = "xyz";
        dropTableIfExists(url, tableName);
        String createTableSQL = "CREATE TABLE " + tableName + " AS SELECT 123 AS x";
        execute(url, getProperties(), createTableSQL);
        String renameColumnSQL = "ALTER TABLE " + tableName + " RENAME COLUMN x TO " + validTargetColumnName;
        execute(url, getProperties(), renameColumnSQL);
        String selectTableSQL = "SELECT " + validTargetColumnName + " FROM " + tableName;
        execute(url, getProperties(), selectTableSQL);
        dropTable(url, tableName);
    }

    @Test
    public void testAlterTableRenameTable()
    {
        String url = TEST_URL;
        String tableName = "test_table_rename";
        String newTableName = "new_table";
        dropTableIfExists(url, tableName);
        dropTableIfExists(url, newTableName);
        String createTableSQL = "CREATE TABLE " + tableName + " AS SELECT 123 AS x";
        execute(url, getProperties(), createTableSQL);
        String renameTableSQL = "ALTER TABLE " + tableName + " RENAME TO " + newTableName;
        execute(url, getProperties(), renameTableSQL);
        String selectTableSQL = "SELECT * FROM " + newTableName;
        execute(url, getProperties(), selectTableSQL);
        dropTable(url, newTableName);
    }

    public void testAlterTableAddColumn()
    {
        String url = TEST_URL;
        String tableName = "test_table_add_column";
        String columnName = "new_column";
        String columnType = "INT";
        dropTableIfExists(url, tableName);
        String createTableSQL = "CREATE TABLE " + tableName + " AS SELECT 123 AS x";
        execute(url, getProperties(), createTableSQL);
        String alterTableSQL = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;
        execute(url, getProperties(), alterTableSQL);
        String selectTableSQL = "SELECT " + columnName + " FROM " + tableName;
        execute(url, getProperties(), selectTableSQL);
        dropTable(url, tableName);
    }

    @Test
    public boolean testAlterTableDropColumn()
    {
        String url = TEST_URL;
        String tableName = "test_table_drop_column";
        String columnName = "x";
        dropTableIfExists(url, tableName);
        String createTableSQL = "CREATE TABLE " + tableName + " AS SELECT 123 AS " + columnName;
        execute(url, getProperties(), createTableSQL);
        String alterTableSQL = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
        execute(url, getProperties(), alterTableSQL);
        String checkColumnExistenceSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "' AND COLUMN_NAME = '" + columnName + "'";
        int count = executeAndGetCount(url, getProperties(), checkColumnExistenceSQL);
        dropTable(url, tableName);
        return count == 0;
    }
}
