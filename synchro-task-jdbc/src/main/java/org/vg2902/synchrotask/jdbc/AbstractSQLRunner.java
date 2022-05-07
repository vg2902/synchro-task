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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.vg2902.synchrotask.core.api.SynchroTask;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.util.Collections.emptyList;
import static org.vg2902.synchrotask.jdbc.ConnectionProperty.AUTO_COMMIT;
import static org.vg2902.synchrotask.jdbc.ConnectionProperty.TRANSACTION_ISOLATION;

/**
 * Base class for {@link SQLRunner} implementations.
 * Provides a common mechanism to save and restore database connection settings that may be changed during
 * {@link SynchroTask} execution.
 */
@Getter(AccessLevel.PUBLIC)
@Slf4j
abstract class AbstractSQLRunner<T> implements SQLRunner<T> {

    protected final Connection connection;
    protected final String tableName;
    protected final SynchroTask<T> task;

    private boolean isInitialized;
    private boolean isClosed;

    private final List<ConnectionPropertyHandler<?>> connectionPropertyHandlers = new ArrayList<>();

    AbstractSQLRunner(DataSource datasource, String tableName, SynchroTask<T> task) throws SQLException {
        this.connection = datasource.getConnection();
        this.task = task;
        this.tableName = tableName;

        isClosed = false;

        setupConnection();
    }

    private void setupConnection() throws SQLException {
        checkNotInitialized();
        checkNotClosed();

        log.debug("Setting up connection {}", connection);

        saveConnectionState();

        connection.setAutoCommit(false);
        connection.setTransactionIsolation(TRANSACTION_READ_COMMITTED);

        isInitialized = true;
    }

    protected void saveConnectionState() {
        log.debug("Saving connection {} properties", connection);

        connectionPropertyHandlers.addAll(getCommonConnectionPropertyHandlers());
        connectionPropertyHandlers.addAll(getCustomConnectionPropertyHandlers());

        connectionPropertyHandlers.forEach(ConnectionPropertyHandler::save);
    }

    protected void restoreConnection() {
        checkNotClosed();

        log.debug("Restoring connection {} properties", connection);

        connectionPropertyHandlers.forEach(ConnectionPropertyHandler::restore);
    }

    @Override
    public void close() throws SQLException {
        if (connection.isClosed())
            return;

        connection.rollback();
        restoreConnection();
        connection.close();

        isClosed = true;
    }

    @Override
    public final boolean isClosed() {
        return isClosed;
    }

    protected final void checkNotClosed() {
        if (isClosed) throw new IllegalStateException("Connection is already closed");
    }

    protected final void checkNotInitialized() {
        if (isInitialized) throw new IllegalStateException("Connection is already initialized");
    }

    private List<ConnectionPropertyHandler<?>> getCommonConnectionPropertyHandlers() {
        return Arrays.asList(
                new ConnectionPropertyHandler<>(this.connection, AUTO_COMMIT, Connection::getAutoCommit, Connection::setAutoCommit),
                new ConnectionPropertyHandler<>(this.connection, TRANSACTION_ISOLATION, Connection::getTransactionIsolation, Connection::setTransactionIsolation));
    }

    protected List<ConnectionPropertyHandler<?>> getCustomConnectionPropertyHandlers() {
        return emptyList();
    }
}