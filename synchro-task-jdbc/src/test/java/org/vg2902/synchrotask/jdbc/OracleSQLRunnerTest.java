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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.vg2902.synchrotask.core.api.LockTimeout;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.vg2902.synchrotask.jdbc.AbstractSQLRunnerIT.getTestSynchroTask;
import static org.vg2902.synchrotask.jdbc.DatabaseIT.TABLE_NAME;

public class OracleSQLRunnerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private final PreparedStatement selectForUpdateStatement = mock(PreparedStatement.class);
    private final PreparedStatement selectForUpdateWaitStatement = mock(PreparedStatement.class);
    private final PreparedStatement selectForUpdateNoWaitStatement = mock(PreparedStatement.class);
    private final ResultSet selectForUpdateResult = mock(ResultSet.class);
    private final ResultSet selectForUpdateWaitResult = mock(ResultSet.class);
    private final ResultSet selectForUpdateNoWaitResult = mock(ResultSet.class);

    private static final int waitTime = 10;

    private static final String selectForUpdateQuery = "SELECT task_name, task_id FROM " + TABLE_NAME + " WHERE task_name = ? AND task_id = ? FOR UPDATE";
    private static final String selectForUpdateWaitQuery = "SELECT task_name, task_id FROM " + TABLE_NAME + " WHERE task_name = ? AND task_id = ? FOR UPDATE WAIT " + waitTime;
    private static final String selectForUpdateNoWaitQuery = "SELECT task_name, task_id FROM " + TABLE_NAME + " WHERE task_name = ? AND task_id = ? FOR UPDATE NOWAIT";

    @Before
    public void init() throws SQLException {
        Mockito.reset(
                dataSource,
                connection,
                metaData,
                selectForUpdateStatement,
                selectForUpdateWaitStatement,
                selectForUpdateNoWaitStatement,
                selectForUpdateResult,
                selectForUpdateWaitResult,
                selectForUpdateNoWaitResult);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("ORACLE");
        when(connection.prepareStatement(selectForUpdateQuery)).thenReturn(selectForUpdateStatement);
        when(connection.prepareStatement(selectForUpdateWaitQuery)).thenReturn(selectForUpdateWaitStatement);
        when(connection.prepareStatement(selectForUpdateNoWaitQuery)).thenReturn(selectForUpdateNoWaitStatement);
        when(selectForUpdateStatement.executeQuery()).thenReturn(selectForUpdateResult);
        when(selectForUpdateWaitStatement.executeQuery()).thenReturn(selectForUpdateWaitResult);
        when(selectForUpdateNoWaitStatement.executeQuery()).thenReturn(selectForUpdateNoWaitResult);
    }

    @Test
    public void setsMaxSupportedLockTimeout() throws SQLException {
        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskName1", "TaskId1", LockTimeout.MAX_SUPPORTED));
        sqlRunner.acquireLock();

        verify(selectForUpdateResult).next();
    }

    @Test
    public void setsZeroLockTimeout() throws SQLException {
        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskName1", "TaskId1", LockTimeout.of(0)));
        sqlRunner.acquireLock();

        verify(selectForUpdateNoWaitResult).next();
    }

    @Test
    public void setsCustomLockTimeout() throws SQLException {
        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskName1", "TaskId1", LockTimeout.of(10000L)));
        sqlRunner.acquireLock();

        verify(selectForUpdateWaitResult).next();
    }
}
