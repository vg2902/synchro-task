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
import org.vg2902.synchrotask.core.api.SynchroTask;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Set;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

/**
 * Implements database-specific features for each supported database engine.
 */
@Slf4j
public enum SynchroTaskSQLSupport {

    /**
     * H2-specific features:
     * <ul>
     *     <li>duplicate key error code is <b>23505</b>;</li>
     *     <li>lock acquire error code is <b>50200</b>;</li>
     *     <li><b>SELECT FOR UPDATE</b> does not support <b>NO WAIT</b> clause. In order to get <b>NO WAIT</b> semantics,
     *     the framework will leverage <b>LOCK_TIMEOUT</b> {@link Connection}-level property. Initial value of
     *     this property is restored prior to returning the connection back to the datasource.</li>
     * </ul>
     */
    H2_SUPPORT {
        private final Set<Integer> duplicateKeyErrorCodes = singleton(23505);
        private final Set<Integer> cannotAcquireLockErrorCodes = singleton(50200);
        private final String UPDATE_TIMEOUT_QUERY = "SET LOCK_TIMEOUT ?";

        @Override
        String getSelectForUpdateNoWaitQuery(String tableName) {
            return getSelectForUpdateQuery(tableName);
        }

        @Override
        ConnectionState setupConnection(Connection connection, SynchroTask<?> task) throws SQLException {
            ConnectionState state = super.setupConnection(connection, task);

            if (task.getCollisionStrategy() == WAIT)
                return state;

            int lockTimeoutInMillis = getLockTimeout(connection);
            log.debug("Current lock timeout: {}", lockTimeoutInMillis);

            state.getProperties().put(ConnectionState.ConnectionProperty.LOCK_TIMEOUT, lockTimeoutInMillis);
            updateLockTimeout(connection, 0);

            return state;
        }

        @Override
        void restoreConnection(Connection connection, ConnectionState state) throws SQLException {
            super.restoreConnection(connection, state);

            Integer lockTimeoutInMillis = (Integer) state.getProperties().get(ConnectionState.ConnectionProperty.LOCK_TIMEOUT);

            if (lockTimeoutInMillis != null)
                updateLockTimeout(connection, lockTimeoutInMillis);
        }

        @Override
        boolean isDuplicateKey(SQLException e) {
            return isExceptionErrorCodeIn(e, duplicateKeyErrorCodes);
        }

        @Override
        boolean isCannotAcquireLock(SQLException e) {
            return isExceptionErrorCodeIn(e, cannotAcquireLockErrorCodes);
        }

        private int getLockTimeout(Connection connection) throws SQLException {
            try (Statement currentTimeout = connection.createStatement()) {
                ResultSet rs = currentTimeout.executeQuery("CALL LOCK_TIMEOUT()");
                rs.next();
                return rs.getInt(1);
            }
        }

        private void updateLockTimeout(Connection connection, int lockTimeoutInMillis) throws SQLException {
            try (PreparedStatement updateTimeout = connection.prepareStatement(UPDATE_TIMEOUT_QUERY)) {
                updateTimeout.setInt(1, lockTimeoutInMillis);
                updateTimeout.executeUpdate();
            }
        }
    },
    /**
     * Oracle-specific features:
     * <ul>
     *     <li>duplicate key error code is <b>1</b>;</li>
     *     <li>lock acquire error code is <b>54</b>;</li>
     * </ul>
     */
    ORACLE_SUPPORT {
        private final Set<Integer> duplicateKeyErrorCodes = singleton(1);
        private final Set<Integer> cannotAcquireLockErrorCodes = singleton(54);

        @Override
        boolean isDuplicateKey(SQLException e) {
            return isExceptionErrorCodeIn(e, duplicateKeyErrorCodes);
        }

        @Override
        boolean isCannotAcquireLock(SQLException e) {
            return isExceptionErrorCodeIn(e, cannotAcquireLockErrorCodes);
        }
    };

    String getInsertQuery(String tableName) {
        return "INSERT INTO " + tableName + "(task_name, task_id, creation_time) VALUES (?, ?, ?)";
    }

    String getSelectForUpdateQuery(String tableName) {
        return "SELECT task_name, task_id FROM " + tableName + " WHERE task_name = ? AND task_id = ? FOR UPDATE";
    }

    String getSelectForUpdateNoWaitQuery(String tableName) {
        return getSelectForUpdateQuery(tableName) + " NOWAIT";
    }

    String getDeleteQuery(String tableName) {
        return "DELETE " + tableName + " WHERE task_name = ? AND task_id = ?";
    }

    ConnectionState setupConnection(Connection connection, SynchroTask<?> task) throws SQLException {
        log.debug("Setting up connection {}", connection);

        ConnectionState state = new ConnectionState();
        state.setAutoCommit(connection.getAutoCommit());
        state.setTransactionIsolation(connection.getTransactionIsolation());

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(TRANSACTION_READ_COMMITTED);

        return state;
    }

    abstract boolean isDuplicateKey(SQLException e);

    abstract boolean isCannotAcquireLock(SQLException e);

    private static boolean isExceptionErrorCodeIn(SQLException e, Collection<Integer> errorCodes) {
        if (e == null)
            return false;

        return ofNullable(errorCodes)
                .orElse(emptyList())
                .contains(e.getErrorCode());
    }

    void restoreConnection(Connection connection, ConnectionState state) throws SQLException {
        log.debug("Restoring connection properties {}", connection);

        connection.setAutoCommit(state.isAutoCommit());
        connection.setTransactionIsolation(state.getTransactionIsolation());
    }

    static SynchroTaskSQLSupport from(Connection connection) throws SQLException {
        return SynchroTaskSQLSupport.from(getProductName(connection));
    }

    private static String getProductName(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String databaseName = metaData.getDatabaseProductName();

        log.debug("Database name: {}", databaseName);
        log.debug("Database version: {}", metaData.getDatabaseProductVersion());

        return databaseName;
    }

    private static SynchroTaskSQLSupport from(String databaseName) {
        String upperCaseDatabaseName = databaseName.toUpperCase();

        try {
            SynchroTaskDatabaseSupport databaseSupport = SynchroTaskDatabaseSupport.valueOf(upperCaseDatabaseName);
            return databaseSupport.sqlSupport;
        } catch (IllegalArgumentException e) {
            throw new UnsupportedDatabaseException(upperCaseDatabaseName);
        }
    }
}
