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
package org.vg2902.synchrotask.jdbc.h2;

import org.junit.BeforeClass;
import org.junit.Test;
import org.vg2902.synchrotask.jdbc.AbstractSQLRunnerIT;
import org.vg2902.synchrotask.jdbc.SQLRunner;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AbstractSQLRunnerIT} implementation for H2. During build, This test class is meant to be executed
 * as part of {@link H2Test} suite.
 */
public class H2SQLRunnerIT extends AbstractSQLRunnerIT implements H2DatabaseIT {

    @BeforeClass
    public static void init() throws IOException, SQLException {
        H2Resource.init();
    }

    @Override
    public Set<Integer> getDuplicateKeyErrorCodes() {
        return singleton(23505);
    }

    @Override
    public Set<Integer> getCannotAcquireLockErrorCodes() {
        return singleton(50200);
    }

    /*
     * H2-specific tests
     */

    @Test
    public void lockTimeoutIsZeroForNonWaitingTasks() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final SQLRunner sqlRunner1 = new SQLRunner(dataSource, TABLE_NAME, getThrowingTestSynchroTask("TaskName1", "TaskId1"));
             final SQLRunner sqlRunner2 = new SQLRunner(dataSource, TABLE_NAME, getReturningTestSynchroTask("TaskName2", "TaskId2"))) {

            assertThat(getLockTimeout(sqlRunner1.getConnection())).isZero();
            assertThat(getLockTimeout(sqlRunner2.getConnection())).isZero();
        }
    }

    @Test
    public void lockTimeoutRemainsUnchangedForWaitingTasks() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {
            assertThat(getLockTimeout(sqlRunner.getConnection())).isEqualTo(H2Resource.DEFAULT_LOCK_TIMEOUT_IN_MILLIS);
        }
    }

    private Long getLockTimeout(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("CALL LOCK_TIMEOUT()");
            rs.next();
            return rs.getLong(1);
        }
    }
}
