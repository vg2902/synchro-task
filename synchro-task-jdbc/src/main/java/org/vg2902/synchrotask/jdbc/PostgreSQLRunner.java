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
import org.vg2902.synchrotask.core.api.LockTimeout;
import org.vg2902.synchrotask.core.api.SynchroTask;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_ALREADY_EXISTS;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_OK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_OK;
import static org.vg2902.synchrotask.jdbc.EntryRemovalResult.REMOVAL_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryRemovalResult.REMOVAL_RESULT_OK;

/**
 * Postgre-specific {@link SQLRunner} implementation with the following features:
 *
 * <ul>
 *     <li>duplicate key error code is <b>23505</b>;</li>
 *     <li>lock acquire error code is <b>55P03</b>;</li>
 *     <li>SQL exception vendor codes are retrieved using {@link SQLException#getSQLState()}</li>
 *     <li><b>SELECT FOR UPDATE</b> does not support <b>WAIT</b> clause.
 *     Instead, the implementation adjusts <b>lock_timeout</b> {@link java.sql.Connection} property.</li>
 * </ul>
 *
 * @see SynchroTask
 * @see SynchroTaskJdbcService
 */
@Getter
@Slf4j
final class PostgreSQLRunner<T> extends AbstractSQLRunner<T> {

    private static final long POSTGRESQL_MAX_LOCK_TIMEOUT_IN_MILLIS = 2147483647L;

    private static final Set<String> duplicateKeyErrorCodes = singleton("23505");
    private static final Set<String> cannotAcquireLockErrorCodes = singleton("55P03");

    private final String insertQuery;
    private final String selectForUpdateQuery;
    private final String selectForUpdateNoWaitQuery;
    private final String deleteQuery;
    private final String getLockTimeoutQuery;
    private final String updateLockTimeoutQuery;

    PostgreSQLRunner(DataSource datasource, String tableName, SynchroTask<T> task) throws SQLException {
        super(datasource, tableName, task);

        insertQuery = "INSERT INTO " + tableName + "(task_id, creation_time) VALUES (?, ?)";
        selectForUpdateQuery = "SELECT task_id FROM " + tableName + " WHERE task_id = ? FOR UPDATE";
        selectForUpdateNoWaitQuery = selectForUpdateQuery + " NOWAIT";
        deleteQuery = "DELETE FROM " + tableName + " WHERE task_id = ?";
        getLockTimeoutQuery = "SHOW lock_timeout";
        updateLockTimeoutQuery = "SET lock_timeout = ?";
    }

    @Override
    public EntryCreationResult createLockEntry() throws SQLException {
        super.checkNotClosed();
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
        log.debug(insertQuery);

        try (PreparedStatement stmt = super.connection.prepareStatement(insertQuery)) {
            stmt.setString(1, task.getTaskId());
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();

            super.connection.commit();
        } catch (SQLException e) {
            super.connection.rollback();
            throw e;
        }
    }

    @Override
    public boolean isDuplicateKey(SQLException e) {
        return isExceptionSQLStateIn(e, duplicateKeyErrorCodes);
    }

    @Override
    public EntryLockResult acquireLock() throws SQLException {
        super.checkNotClosed();
        log.debug("Acquiring lock for {}", task);

        LockTimeout lockTimeout = getTask().getLockTimeout();
        boolean isLockTimeoutAdjustmentRequired = lockTimeout != SYSTEM_DEFAULT;

        int currentLockTimeoutInSeconds = 0;
        if (isLockTimeoutAdjustmentRequired)
            currentLockTimeoutInSeconds = adjustLockTimeout();

        try {
            boolean noWait = lockTimeout.getValueInMillis() == 0;
            boolean isLocked = selectForUpdate(noWait);

            if (!isLocked)
                return LOCK_RESULT_NOT_FOUND;
        } catch (SQLException e) {
            if (isCannotAcquireLock(e)) {
                return LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
            } else {
                throw e;
            }
        } finally {
            if (isLockTimeoutAdjustmentRequired)
                updateLockTimeout(currentLockTimeoutInSeconds);
        }

        return LOCK_RESULT_OK;
    }

    private int adjustLockTimeout() throws SQLException {
        LockTimeout lockTimeout = getTask().getLockTimeout();
        int currentLockTimeoutInMillis = getLockTimeoutInMillis();

        log.debug("Current lock timeout: {}ms", currentLockTimeoutInMillis);

        long lockTimeoutInMillisOverride = lockTimeout.getValueInMillis();

        if (lockTimeoutInMillisOverride > POSTGRESQL_MAX_LOCK_TIMEOUT_IN_MILLIS)
            log.warn("Provided lock timeout " + lockTimeoutInMillisOverride + " exceeds PostgreSQL max supported value and will be adjusted to " + POSTGRESQL_MAX_LOCK_TIMEOUT_IN_MILLIS);

        if (lockTimeout == LockTimeout.MAX_SUPPORTED || lockTimeoutInMillisOverride > POSTGRESQL_MAX_LOCK_TIMEOUT_IN_MILLIS)
            lockTimeoutInMillisOverride = POSTGRESQL_MAX_LOCK_TIMEOUT_IN_MILLIS;

        updateLockTimeout((int) lockTimeoutInMillisOverride);

        return currentLockTimeoutInMillis;
    }

    private boolean selectForUpdate(boolean noWait) throws SQLException {
        String sql = noWait ? selectForUpdateNoWaitQuery : selectForUpdateQuery;
        log.debug(sql);

        try (PreparedStatement stmt = super.connection.prepareStatement(sql)) {
            stmt.setString(1, task.getTaskId());

            ResultSet resultSet = stmt.executeQuery();

            return resultSet.next();
        } catch (SQLException e) {
            super.connection.rollback();
            throw e;
        }
    }

    private int getLockTimeoutInMillis() throws SQLException {
        log.debug(getLockTimeoutQuery);

        try (Statement stmt = super.connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(getLockTimeoutQuery);
            rs.next();
            return rs.getInt(1);
        }
    }

    private void updateLockTimeout(int lockTimeoutInSeconds) throws SQLException {
        log.debug("Updating lock timeout to: {}", lockTimeoutInSeconds);

        String updateTimeoutQuery = updateLockTimeoutQuery.replace("?", Integer.toString(lockTimeoutInSeconds));
        log.debug(updateTimeoutQuery);

        try (PreparedStatement updateTimeout = super.connection.prepareStatement(updateTimeoutQuery)) {
            updateTimeout.executeUpdate();
        } catch (SQLException e) {
            super.connection.rollback();
            throw e;
        }
    }

    @Override
    public boolean isCannotAcquireLock(SQLException e) {
        return isExceptionSQLStateIn(e, cannotAcquireLockErrorCodes);
    }

    @Override
    public EntryRemovalResult removeLockEntry() throws SQLException {
        super.checkNotClosed();

        if (delete())
            return REMOVAL_RESULT_OK;
        else
            return REMOVAL_RESULT_NOT_FOUND;
    }

    private boolean delete() throws SQLException {
        log.debug(deleteQuery);

        try (PreparedStatement stmt = super.connection.prepareStatement(deleteQuery)) {
            stmt.setString(1, task.getTaskId());

            int cnt = stmt.executeUpdate();

            super.connection.commit();

            return cnt > 0;
        } catch (SQLException e) {
            super.connection.rollback();
            throw e;
        }
    }
}