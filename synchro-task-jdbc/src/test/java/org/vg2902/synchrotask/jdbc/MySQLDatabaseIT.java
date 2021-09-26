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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * MySQL-specific version of {@link DatabaseIT}.
 */
public interface MySQLDatabaseIT extends DatabaseIT {

    @Override
    default DataSource getDataSource() {
        return MySQLResource.datasource;
    }

    @Override
    default Long getSessionId(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT connection_id()");
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default boolean isDatabaseSessionBlocked(Long blockedSessionId, Long blockingSessionId) {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement statement = connection.prepareStatement(
                     "select 1" +
                     "  from performance_schema.data_lock_waits w" +
                     "  join performance_schema.threads tr" +
                     "    on tr.thread_id = w.requesting_thread_id" +
                     "  join performance_schema.threads tb" +
                     "    on tb.thread_id = w.blocking_thread_id" +
                     " where tr.processlist_id = ?" +
                     "   and tb.processlist_id = ?")) {

            statement.setLong(1, blockedSessionId);
            statement.setLong(2, blockingSessionId);

            ResultSet rs = statement.executeQuery();
            return rs.next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
