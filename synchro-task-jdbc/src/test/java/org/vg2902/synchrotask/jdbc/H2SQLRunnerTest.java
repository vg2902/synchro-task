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
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.vg2902.synchrotask.jdbc.AbstractSQLRunnerIT.getTestSynchroTask;
import static org.vg2902.synchrotask.jdbc.DatabaseIT.TABLE_NAME;

public class H2SQLRunnerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private final Statement getLockTimeoutStatement = mock(Statement.class);
    private final ResultSet lockTimeoutResult = mock(ResultSet.class);
    private final PreparedStatement selectForUpdateStatement = mock(PreparedStatement.class);
    private final ResultSet selectForUpdateResult = mock(ResultSet.class);
    private final PreparedStatement updateLockTimeoutStatement1 = mock(PreparedStatement.class);
    private final PreparedStatement updateLockTimeoutStatement2 = mock(PreparedStatement.class);

    private static final String getLockTimeoutQuery = "CALL LOCK_TIMEOUT()";
    private static final String updateLockTimeoutQuery = "SET LOCK_TIMEOUT ?";
    private static final String selectForUpdateQuery = "SELECT task_id FROM " + TABLE_NAME + " WHERE task_id = ? FOR UPDATE";

    @Before
    public void init() throws SQLException {
        Mockito.reset(
                dataSource,
                connection,
                metaData,
                getLockTimeoutStatement,
                lockTimeoutResult,
                selectForUpdateStatement,
                selectForUpdateResult,
                updateLockTimeoutStatement1,
                updateLockTimeoutStatement2);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(connection.createStatement()).thenReturn(getLockTimeoutStatement);
        when(getLockTimeoutStatement.executeQuery(getLockTimeoutQuery)).thenReturn(lockTimeoutResult);
        when(connection.prepareStatement(selectForUpdateQuery)).thenReturn(selectForUpdateStatement);
        when(selectForUpdateStatement.executeQuery()).thenReturn(selectForUpdateResult);

        when(connection.prepareStatement(updateLockTimeoutQuery))
                .thenReturn(updateLockTimeoutStatement1)
                .thenReturn(updateLockTimeoutStatement2);
    }

    @Test
    public void setsMaxSupportedLockTimeout() throws SQLException {
        int currentTimeoutInMillis = 1000;
        when(lockTimeoutResult.getInt(1)).thenReturn(currentTimeoutInMillis);

        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.MAX_SUPPORTED));
        sqlRunner.acquireLock();

        verify(connection, times(2)).prepareStatement(updateLockTimeoutQuery);
        verify(updateLockTimeoutStatement1).setInt(1, Integer.MAX_VALUE);
        verify(updateLockTimeoutStatement1).executeUpdate();

        verify(selectForUpdateResult).next();

        verify(updateLockTimeoutStatement2).setInt(1, currentTimeoutInMillis);
        verify(updateLockTimeoutStatement2).executeUpdate();
    }

    @Test
    public void setsZeroLockTimeout() throws SQLException {
        int currentTimeoutInMillis = 1000;
        when(lockTimeoutResult.getInt(1)).thenReturn(currentTimeoutInMillis);

        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.of(0)));
        sqlRunner.acquireLock();

        verify(connection, times(2)).prepareStatement(updateLockTimeoutQuery);
        verify(updateLockTimeoutStatement1).setInt(1, 0);
        verify(updateLockTimeoutStatement1).executeUpdate();

        verify(selectForUpdateResult).next();

        verify(updateLockTimeoutStatement2).setInt(1, currentTimeoutInMillis);
        verify(updateLockTimeoutStatement2).executeUpdate();
    }

    @Test
    public void setsCustomLockTimeout() throws SQLException {
        int currentTimeoutInMillis = 1000;
        when(lockTimeoutResult.getInt(1)).thenReturn(currentTimeoutInMillis);

        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.of(10000L)));
        sqlRunner.acquireLock();

        verify(connection, times(2)).prepareStatement(updateLockTimeoutQuery);
        verify(updateLockTimeoutStatement1).setInt(1, 10000);
        verify(updateLockTimeoutStatement1).executeUpdate();

        verify(selectForUpdateResult).next();

        verify(updateLockTimeoutStatement2).setInt(1, currentTimeoutInMillis);
        verify(updateLockTimeoutStatement2).executeUpdate();
    }

    @Test
    public void doesNotUpdateLockTimeout() throws SQLException {
        final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.SYSTEM_DEFAULT));
        sqlRunner.acquireLock();
        verify(connection, never()).prepareStatement(argThat(query -> query.toUpperCase().contains("LOCK_TIMEOUT")));
    }
}
