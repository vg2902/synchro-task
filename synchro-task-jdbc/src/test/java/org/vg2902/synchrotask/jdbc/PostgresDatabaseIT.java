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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Postgres-specific version of {@link DatabaseIT}.
 */
public interface PostgresDatabaseIT extends DatabaseIT {

    @Override
    default DataSource getDataSource() {
        return PostgresResource.datasource;
    }

    @Override
    default Long getSessionId(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT pg_backend_pid()");
            rs.next();
            return (long) rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default boolean isDatabaseSessionBlocked(Long blockedSessionId, Long blockingSessionId) {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement statement = connection.prepareStatement("SELECT * FROM unnest(pg_blocking_pids(?))")) {

            statement.setInt(1, blockedSessionId.intValue());

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                if (rs.getInt(1) == blockingSessionId)
                    return true;
            }

            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
