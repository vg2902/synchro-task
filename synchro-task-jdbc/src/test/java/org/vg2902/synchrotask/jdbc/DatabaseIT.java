/*
 * Copyright 2021 vg2902.org
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Contains common methods for database tests.
 */
public interface DatabaseIT {

    String TABLE_NAME = "synchro_task";

    DataSource getDataSource();

    default void cleanup() throws SQLException {
        DataSource dataSource = getDataSource();

        try (Connection connection = dataSource.getConnection()) {
            cleanupTable(connection, TABLE_NAME);
        }
    }

    default void cleanupTable(Connection connection, String table) throws SQLException {
        Statement statement = connection.createStatement();
        statement.executeUpdate("DELETE FROM " + table);
        connection.commit();
    }

    Long getSessionId(Connection connection);

    boolean isDatabaseSessionBlocked(Long blockedSessionId, Long blockingSessionId);
}
