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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.vg2902.synchrotask.core.api.SynchroTask;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_ALREADY_EXISTS;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_OK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_OK;
import static org.vg2902.synchrotask.jdbc.EntryRemovalResult.REMOVAL_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryRemovalResult.REMOVAL_RESULT_OK;

/**
 * Oracle-specific {@link SQLRunner} implementation with the following features:
 *
 * <ul>
 *     <li>duplicate key error code is <b>1</b>;</li>
 *     <li>lock acquire error code is <b>54</b>;</li>
 *     <li>SQL exception vendor codes are retrieved using {@link SQLException#getErrorCode()}</li>
 * </ul>
 *
 * @see SynchroTask
 * @see SynchroTaskJdbcService
 */
@Getter
@Slf4j
final class OracleSQLRunner<T> extends AbstractSQLRunner<T> {

    private static final Set<Integer> duplicateKeyErrorCodes = singleton(1);
    private static final Set<Integer> cannotAcquireLockErrorCodes = singleton(54);

    private final String insertQuery;
    private final String selectForUpdateQuery;
    private final String selectForUpdateNoWaitQuery;
    private final String deleteQuery;

    OracleSQLRunner(DataSource datasource, String tableName, SynchroTask<T> task) throws SQLException {
        super(datasource, tableName, task);

        insertQuery = "INSERT INTO " + tableName + "(task_name, task_id, creation_time) VALUES (?, ?, ?)";
        selectForUpdateQuery = "SELECT task_name, task_id FROM " + tableName + " WHERE task_name = ? AND task_id = ? FOR UPDATE";
        selectForUpdateNoWaitQuery = selectForUpdateQuery + " NOWAIT";
        deleteQuery = "DELETE FROM " + tableName + " WHERE task_name = ? AND task_id = ?";
    }

    @Override
    public EntryCreationResult createLockEntry() throws SQLException {
        log.debug("Creating a lock entry for {}", task);

        try {
            insert();
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                log.debug("Lock entry for {} already exists", task);
                return CREATION_RESULT_ALREADY_EXISTS;
            } else {
                throw e;
            }
        }

        log.debug("Lock entry for {} is successfully created", task);
        return CREATION_RESULT_OK;
    }

    private void insert() throws SQLException {
        super.checkNotClosed();

        log.debug(insertQuery);

        try (PreparedStatement stmt = super.connection.prepareStatement(insertQuery)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();

            super.connection.commit();
        }
    }

    @Override
    public boolean isDuplicateKey(SQLException e) {
        return isExceptionErrorCodeIn(e, duplicateKeyErrorCodes);
    }

    @Override
    public EntryLockResult acquireLock() throws SQLException {
        log.debug("Acquiring lock for {}", task);

        try {
            boolean noWait = task.getCollisionStrategy() != WAIT;
            boolean isLocked = selectForUpdate(noWait);

            if (!isLocked)
                return LOCK_RESULT_NOT_FOUND;
        } catch (SQLException e) {
            if (isCannotAcquireLock(e)) {
                return LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
            } else {
                throw e;
            }
        }

        return LOCK_RESULT_OK;
    }

    private boolean selectForUpdate(boolean noWait) throws SQLException {
        super.checkNotClosed();

        String sql = noWait ? selectForUpdateNoWaitQuery : selectForUpdateQuery;
        log.debug(sql);

        try (PreparedStatement stmt = super.connection.prepareStatement(sql)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));

            ResultSet resultSet = stmt.executeQuery();

            return resultSet.next();
        }
    }

    @Override
    public boolean isCannotAcquireLock(SQLException e) {
        return isExceptionErrorCodeIn(e, cannotAcquireLockErrorCodes);
    }

    @Override
    public EntryRemovalResult removeLockEntry() throws SQLException {
        if (delete())
            return REMOVAL_RESULT_OK;
        else
            return REMOVAL_RESULT_NOT_FOUND;
    }

    private boolean delete() throws SQLException {
        super.checkNotClosed();

        log.debug(deleteQuery);

        try (PreparedStatement stmt = super.connection.prepareStatement(deleteQuery)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));

            int cnt = stmt.executeUpdate();

            super.connection.commit();

            return cnt > 0;
        }
    }
}