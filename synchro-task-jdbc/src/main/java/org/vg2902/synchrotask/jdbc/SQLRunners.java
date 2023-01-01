/*
 * Copyright 2021-2023 vg2902.org
 *
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
package org.vg2902.synchrotask.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Slf4j
final class SQLRunners {

    private SQLRunners() {
    }

    static <T> SQLRunner<T> create(DataSource datasource, String tableName, SynchroTask<T> task) {
        try {
            SynchroTaskDatabaseSupport database = from(getProductName(datasource));

            switch (database) {
                case H2:
                    return new H2SQLRunner<>(datasource, tableName, task);
                case MYSQL:
                    return new MySQLRunner<>(datasource, tableName, task);
                case ORACLE:
                    return new OracleSQLRunner<>(datasource, tableName, task);
                case POSTGRESQL:
                    return new PostgreSQLRunner<>(datasource, tableName, task);
                default:
                    throw new UnsupportedDatabaseException(database.name());
            }

        } catch (SQLException e) {
            throw new SynchroTaskException(e);
        }
    }

    private static String getProductName(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = metaData.getDatabaseProductName();

            log.debug("Database name: {}", databaseName);
            log.debug("Database version: {}", metaData.getDatabaseProductVersion());

            return databaseName;
        }
    }

    private static SynchroTaskDatabaseSupport from(String databaseName) {
        String upperCaseDatabaseName = databaseName.toUpperCase();

        try {
            return SynchroTaskDatabaseSupport.valueOf(upperCaseDatabaseName);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedDatabaseException(upperCaseDatabaseName);
        }
    }
}
