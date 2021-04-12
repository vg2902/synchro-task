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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * Implements low-level SQL operations required by the framework to maintain the given task control row
 * in the registry table.
 *
 * @see SynchroTask
 * @see SynchroTaskJdbcService
 */
@Getter
@Slf4j
public class SQLRunner implements AutoCloseable {

    private final SynchroTaskSQLSupport sqlSupport;
    private final Connection connection;
    private final String tableName;
    private final SynchroTask<?> task;
    private final ConnectionState connectionState;

    public SQLRunner(DataSource datasource, String tableName, SynchroTask<?> task) throws SQLException {
        this.connection = datasource.getConnection();
        this.task = task;
        this.tableName = tableName;
        this.sqlSupport = SynchroTaskSQLSupport.from(this.connection);

        this.connectionState = this.sqlSupport.setupConnection(connection, task);
    }

    /**
     * Inserts the given task control row into the registry table and commits current transaction.
     *
     * @throws SQLException in case of any exception at the database level
     */
    public void insert() throws SQLException {
        String sql = sqlSupport.getInsertQuery(tableName);
        log.debug(sql);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
        }

        connection.commit();
    }

    /**
     * Executes <b>SELECT FOR UPDATE</b> statement for the given task control row.
     * Current transaction remains uncommitted.
     *
     * @param noWait whether <b>SELECT FOR UPDATE</b> statement should be executed with <b>NO WAIT</b> clause
     * @return <b>true</b> if the the statement completed successfully returning the row,
     * <b>false</b> if the row was not found
     * @throws SQLException in case of any exception at the database level
     */
    public boolean selectForUpdate(boolean noWait) throws SQLException {
        String sql = noWait ? sqlSupport.getSelectForUpdateNoWaitQuery(tableName) : sqlSupport.getSelectForUpdateQuery(tableName);
        log.debug(sql);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));

            ResultSet resultSet = stmt.executeQuery();

            return resultSet.next();
        }
    }

    /**
     * Deletes the given task control row and commits current transaction.
     *
     * @return <b>true</b> if the statement completed successfully deleting the row,
     * <b>false</b> if the control row was not found
     * @throws SQLException in case of any exception at the database level
     */
    public boolean delete() throws SQLException {
        String sql = sqlSupport.getDeleteQuery(tableName);
        log.debug(sql);

        int cnt;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.valueOf(task.getTaskName()));
            stmt.setString(2, String.valueOf(task.getTaskId()));

            cnt = stmt.executeUpdate();
        }

        connection.commit();
        return cnt > 0;
    }

    /**
     * Checks whether the given exception instance represents locking failure
     *
     * @param e {@link SQLException} instance
     * @return <b>true</b> if the exception occurred because the database failed to acquire a lock,
     * <b>false</b> otherwise
     */
    public boolean isCannotAcquireLock(SQLException e) {
        return isExceptionErrorCodeIn(e, sqlSupport.getCannotAcquireLockErrorCodes());
    }

    /**
     * Checks whether the given exception instance represents is caused by duplicated key
     *
     * @param e {@link SQLException} instance
     * @return <b>true</b> if the exception occurred because the database failed due to duplicated key,
     * <b>false</b> otherwise
     */
    public boolean isDuplicateKey(SQLException e) {
        return isExceptionErrorCodeIn(e, sqlSupport.getDuplicateKeyErrorCodes());
    }

    public void close() throws SQLException {
        if (connection.isClosed())
            return;

        sqlSupport.restoreConnection(this.connection, this.connectionState);
        connection.close();
    }

    private boolean isExceptionErrorCodeIn(SQLException e, Collection<Integer> errorCodes) {
        if (e == null)
            return false;

        return ofNullable(errorCodes)
                .orElse(emptyList())
                .contains(e.getErrorCode());
    }
}