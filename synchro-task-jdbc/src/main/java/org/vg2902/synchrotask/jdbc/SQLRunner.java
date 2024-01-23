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

import org.vg2902.synchrotask.core.api.SynchroTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * Encapsulates low-level database operations for creating, locking and removing a control row in the registry table
 * for the given {@link SynchroTask} instance.
 * <p>
 * Implementations are expected to be parameterized by the following arguments:
 * <ul>
 *     <li>a {@link SynchroTask} instance</li>
 *     <li>a {@link Connection} to execute SQL commands</li>
 *     <li>the registry table name</li>
 * </ul>
 * <p>
 * The general contract for the implementations is to ensure that the underlying {@link Connection} is closed when
 * {@link #close()} method is called, and all the connection properties that have been changed since the instance creation
 * are restored before closing.
 * Once closed, an instance should not allow further use, such attempts should throw an {@link IllegalStateException}.
 *
 * @param <T> underlying {@link SynchroTask} return type
 * @see SynchroTask
 * @see SynchroTaskJdbcService
 */
interface SQLRunner<T> extends AutoCloseable {

    /**
     * Inserts a control row for the given {@link SynchroTask} into the registry table.
     * This method should commit the current transaction.
     *
     * @return {@link EntryCreationResult}
     * @throws SQLException in case of any exception at the database level
     */
    EntryCreationResult createLockEntry() throws SQLException;

    /**
     * Locks the control row created in the registry table for the underlying {@link SynchroTask}.
     * <p>
     * If the row is already locked by another session, the method should wait for it to release the lock for at most the
     * amount of time specified in the task {@link org.vg2902.synchrotask.core.api.LockTimeout} settings.
     * <p>
     * If the lock is acquired successfully, the method should leave the current transaction uncommitted.
     * <p>
     * Implementations should check for specific vendor error codes to correctly identify a locking exception
     *
     * @return {@link EntryLockResult}
     * @throws SQLException in case of any exception at the database level
     */
    EntryLockResult acquireLock() throws SQLException;

    /**
     * Deletes a control row for the given {@link SynchroTask}.
     * This method should commit the current transaction.
     *
     * @return {@link EntryRemovalResult}
     * @throws SQLException in case of any exception at the database level
     */
    EntryRemovalResult removeLockEntry() throws SQLException;

    /**
     * Checks whether the given exception is caused by locking failure
     *
     * @param e {@link SQLException} instance
     * @return <b>true</b> if the exception occurred because the database failed to acquire a lock,
     * <b>false</b> otherwise
     */
    boolean isCannotAcquireLock(SQLException e);

    /**
     * Checks whether the given exception is caused by duplicated key
     *
     * @param e {@link SQLException} instance
     * @return <b>true</b> if the exception occurred because the database failed due to duplicated key,
     * <b>false</b> otherwise
     */
    boolean isDuplicateKey(SQLException e);

    @Override
    void close() throws SQLException;

    /**
     * Checks whether this instance is closed
     *
     * @return <b>true</b> if {@link #close()} method has already been called, <b>false</b> otherwise
     */
    boolean isClosed();

    /**
     * @return underlying {@link SynchroTask}
     */
    SynchroTask<T> getTask();


    /**
     * @return the registry table name
     */
    String getTableName();

    /**
     * @return underlying {@link Connection}
     */
    Connection getConnection();

    default boolean isExceptionErrorCodeIn(SQLException e, Collection<Integer> errorCodes) {
        if (e == null)
            return false;

        return ofNullable(errorCodes)
                .orElse(emptyList())
                .contains(e.getErrorCode());
    }

    default boolean isExceptionSQLStateIn(SQLException e, Collection<String> errorCodes) {
        if (e == null)
            return false;

        return ofNullable(errorCodes)
                .orElse(emptyList())
                .contains(e.getSQLState());
    }
}
