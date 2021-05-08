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

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.Table;
import org.junit.After;
import org.junit.Test;
import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTask;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link SQLRunner} methods.
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

        try (final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {

            sqlRunner.insert();
            Table synchroTaskAfter = new Table(dataSource, TABLE_NAME);

            org.assertj.db.api.SoftAssertions dbAssertions = new org.assertj.db.api.SoftAssertions();
            dbAssertions.assertThat(synchroTaskAfter).hasNumberOfRows(1);
            dbAssertions.assertThat(synchroTaskAfter).row(0)
                    .column("TASK_NAME").value().isEqualTo("TaskName1")
                    .column("TASK_ID").value().isEqualTo("TaskId1")
                    .column("CREATION_TIME").value().isAfterOrEqualTo(now);

            dbAssertions.assertAll();
        }
    }

    @Test
    public void selectsLockEntryForUpdateIfNotLocked() throws SQLException {
        selectsLockEntryIfNotLocked(false);
    }

    @Test
    public void selectsLockEntryForUpdateNoWaitIfNotLocked() throws SQLException {
        selectsLockEntryIfNotLocked(true);
    }

    public void selectsLockEntryIfNotLocked(boolean noWait) throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner sqlRunner1 = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"));
             final SQLRunner sqlRunner2 = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName2", "TaskId2"));
             final SQLRunner sqlRunner3 = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName3", "TaskId3"))) {

            statement.executeUpdate("INSERT INTO SYNCHRO_TASK(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO SYNCHRO_TASK(task_name, task_id, creation_time) VALUES ('TaskName2', 'TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();


            SoftAssertions assertions = new SoftAssertions();
            assertions.assertThat(sqlRunner1.selectForUpdate(noWait)).isTrue();
            assertions.assertThat(sqlRunner2.selectForUpdate(noWait)).isTrue();
            assertions.assertThat(sqlRunner3.selectForUpdate(noWait)).isFalse();
            assertions.assertAll();
        }
    }

    @Test
    public void locksForUpdateIfNotLocked() throws SQLException {
        locksIfNotLocked(false);
    }

    @Test
    public void locksForUpdateNoWaitIfNotLocked() throws SQLException {
        locksIfNotLocked(true);
    }

    public void locksIfNotLocked(boolean noWait) throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {

            Long synchroTaskSessionId = getSessionId(sqlRunner.getConnection());
            Long testSessionId = getSessionId(connection);

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName2', 'TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();

            boolean isFound = sqlRunner.selectForUpdate(noWait);

            new Thread(() -> lockInNewSession(connection, "TaskName1", "TaskId1")).start();
            await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(() -> isDatabaseSessionBlocked(testSessionId, synchroTaskSessionId));
            sqlRunner.getConnection().commit();

            assertThat(isFound).isTrue();
        }
    }

    private void lockInNewSession(Connection connection, String taskName, String taskId) {
        try (final PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE task_name = ? AND task_id = ? FOR UPDATE")) {
            statement.setString(1, taskName);
            statement.setString(2, taskId);
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
             final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {

            Long synchroTaskSessionId = getSessionId(sqlRunner.getConnection());
            Long testSessionId = getSessionId(connection);

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName2', 'TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();
            statement.executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE task_name = 'TaskName1' AND task_id = 'TaskId1' FOR UPDATE");

            new Thread(() -> {
                try {
                    sqlRunner.selectForUpdate(false);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(() -> isDatabaseSessionBlocked(synchroTaskSessionId, testSessionId));
            connection.commit();

            boolean isFound = sqlRunner.selectForUpdate(false);
            assertThat(isFound).isTrue();
        }
    }

    @Test
    public void cannotAcquireLockIfAlreadyLocked() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getThrowingTestSynchroTask("TaskName1", "TaskId1"))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:01')");
            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName2', 'TaskId2', TIMESTAMP '2000-01-01 00:00:02')");
            connection.commit();
            statement.executeQuery("SELECT * FROM " + TABLE_NAME + " WHERE task_name = 'TaskName1' AND task_id = 'TaskId1' FOR UPDATE");


            assertThatThrownBy(() -> sqlRunner.selectForUpdate(true)).matches(e -> getSQLSupport().isCannotAcquireLock(((SQLException) e)));
        }
    }

    @Test
    public void deletesLockEntry() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:00')");
            connection.commit();
            boolean deleted = sqlRunner.delete();


            org.assertj.core.api.Assertions.assertThat(deleted).isTrue();

            Table synchroTaskAfter = new Table(dataSource, TABLE_NAME);
            org.assertj.db.api.Assertions.assertThat(synchroTaskAfter).hasNumberOfRows(0);
        }
    }

    @Test
    public void throwsDuplicateKeyExceptionIfEntryAlreadyExists() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {

            statement.executeUpdate("INSERT INTO " + TABLE_NAME + "(task_name, task_id, creation_time) VALUES ('TaskName1', 'TaskId1', TIMESTAMP '2000-01-01 00:00:00')");
            connection.commit();


            assertThatThrownBy(sqlRunner::insert).matches(e -> getSQLSupport().isDuplicateKey((SQLException) e));
        }
    }

    public SynchroTask<Void> getWaitingTestSynchroTask(String taskName, String taskId) {
        return getTestSynchroTask(taskName, taskId, CollisionStrategy.WAIT);
    }

    public SynchroTask<Void> getThrowingTestSynchroTask(String taskName, String taskId) {
        return getTestSynchroTask(taskName, taskId, CollisionStrategy.THROW);
    }

    public SynchroTask<Void> getReturningTestSynchroTask(String taskName, String taskId) {
        return getTestSynchroTask(taskName, taskId, CollisionStrategy.RETURN);
    }

    private SynchroTask<Void> getTestSynchroTask(String taskName, String taskId, CollisionStrategy strategy) {
        return SynchroTask.from(() -> {})
                .withName(taskName)
                .withId(taskId)
                .onLock(strategy)
                .build();
    }
}
