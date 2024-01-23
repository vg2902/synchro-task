/*
 * Copyright 2021-2024 vg2902.org
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
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.Table;
import org.junit.After;
import org.junit.Test;
import org.vg2902.synchrotask.core.api.LockTimeout;
import org.vg2902.synchrotask.core.api.SynchroTask;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_ALREADY_EXISTS;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_OK;

/**
 * Tests for {@link AbstractSQLRunner} methods.
 * These tests require real database instance to be up and running.
 */
@Slf4j
public abstract class AbstractSQLRunnerIT implements DatabaseIT {

    private static final long WAITING_SECONDS = 5;

    @After
    public void afterTest() throws SQLException {
        log.info("Cleanup started");
        cleanup();
        log.info("Cleanup completed");
    }

    @Test
    public void insertsLockEntry() throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        DataSource dataSource = getDataSource();

        try (final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.SYSTEM_DEFAULT))) {

            sqlRunner.createLockEntry();
            Table synchroTaskAfter = new Table(dataSource, TABLE_NAME);

            org.assertj.db.api.SoftAssertions dbAssertions = new org.assertj.db.api.SoftAssertions();
            dbAssertions.assertThat(synchroTaskAfter).hasNumberOfRows(1);
            dbAssertions.assertThat(synchroTaskAfter).row(0)
                    .column("TASK_ID").value().isEqualTo("TaskId1")
                    .column("CREATION_TIME").value().isAfterOrEqualTo(now);

            dbAssertions.assertAll();
        }
    }

    @Test
    public void selectsLockEntryForUpdateIfNotLocked() throws SQLException {
        selectsLockEntryIfNotLocked(LockTimeout.MAX_SUPPORTED);
    }

    @Test
    public void selectsLockEntryForUpdateNoWaitIfNotLocked() throws SQLException {
        selectsLockEntryIfNotLocked(LockTimeout.of(0));
    }

    public void selectsLockEntryIfNotLocked(LockTimeout lockTimeout) throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner1 = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", lockTimeout));
             final SQLRunner<Void> sqlRunner2 = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId2", lockTimeout));
             final SQLRunner<Void> sqlRunner3 = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId3", lockTimeout))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();


            SoftAssertions assertions = new SoftAssertions();
            assertions.assertThat(sqlRunner1.acquireLock()).isEqualTo(LOCK_RESULT_OK);
            assertions.assertThat(sqlRunner2.acquireLock()).isEqualTo(LOCK_RESULT_OK);
            assertions.assertThat(sqlRunner3.acquireLock()).isEqualTo(LOCK_RESULT_NOT_FOUND);
            assertions.assertAll();

            sqlRunner1.getConnection().commit();
            sqlRunner2.getConnection().commit();
            sqlRunner3.getConnection().commit();
        }
    }

    @Test
    public void locksForUpdateIfNotLocked() throws SQLException {
        locksIfNotLocked(LockTimeout.MAX_SUPPORTED);
    }

    @Test
    public void locksForUpdateNoWaitIfNotLocked() throws SQLException {
        locksIfNotLocked(LockTimeout.of(0));
    }

    public void locksIfNotLocked(LockTimeout lockTimeout) throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", lockTimeout))) {

            Long synchroTaskSessionId = getSessionId(sqlRunner.getConnection());
            Long testSessionId = getSessionId(connection);

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();

            EntryLockResult result = sqlRunner.acquireLock();

            new Thread(() -> lockInNewSession(connection, "TaskId1")).start();
            await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(() -> isDatabaseSessionBlocked(testSessionId, synchroTaskSessionId));
            sqlRunner.getConnection().commit();

            assertThat(result).isEqualTo(LOCK_RESULT_OK);
        }
    }

    private void lockInNewSession(Connection connection, String taskId) {
        try (final PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE task_id = ? FOR UPDATE")) {
            statement.setString(1, taskId);
            statement.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void waitsIfAlreadyLocked() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.SYSTEM_DEFAULT))) {

            Long synchroTaskSessionId = getSessionId(sqlRunner.getConnection());
            Long testSessionId = getSessionId(connection);

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();
            statement.executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE task_id = 'TaskId1' FOR UPDATE");

            new Thread(() -> {
                try {
                    sqlRunner.acquireLock();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(() -> isDatabaseSessionBlocked(synchroTaskSessionId, testSessionId));
            connection.commit();

            EntryLockResult result = sqlRunner.acquireLock();
            assertThat(result).isEqualTo(LOCK_RESULT_OK);
            sqlRunner.getConnection().commit();
        }
    }

    @Test
    public void cannotAcquireLockIfAlreadyLocked() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.of(0)))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();
            statement.executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE task_id = 'TaskId1' FOR UPDATE");


            assertThat(sqlRunner.acquireLock()).isEqualTo(LOCK_RESULT_LOCKED_BY_ANOTHER_TASK);
        }
    }

    @Test
    public void deletesLockEntry() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.SYSTEM_DEFAULT))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:00')");
            connection.commit();
            EntryRemovalResult result = sqlRunner.removeLockEntry();


            org.assertj.core.api.Assertions.assertThat(result).isEqualTo(EntryRemovalResult.REMOVAL_RESULT_OK);

            Table synchroTaskAfter = new Table(dataSource, TABLE_NAME);
            org.assertj.db.api.Assertions.assertThat(synchroTaskAfter).hasNumberOfRows(0);
        }
    }

    @Test
    public void throwsDuplicateKeyExceptionIfEntryAlreadyExists() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner<Void> sqlRunner = SQLRunners.create(dataSource, TABLE_NAME, getTestSynchroTask("TaskId1", LockTimeout.SYSTEM_DEFAULT))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_id, creation_time) VALUES ('TaskId1', TIMESTAMP '2000-01-01 00:00:00')");
            connection.commit();


            assertThat(sqlRunner.createLockEntry()).isEqualTo(CREATION_RESULT_ALREADY_EXISTS);
        }
    }

    static SynchroTask<Void> getTestSynchroTask(String taskId, LockTimeout lockTimeout) {
        return SynchroTask.from(() -> {})
                .withId(taskId)
                .withLockTimeout(lockTimeout)
                .build();
    }
}
