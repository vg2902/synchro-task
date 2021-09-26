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
import org.vg2902.synchrotask.core.exception.SynchroTaskException;
import org.vg2902.synchrotask.core.utils.ThrowableBiConsumer;
import org.vg2902.synchrotask.core.utils.ThrowableFunction;

import java.sql.Connection;
import java.sql.SQLException;

@Getter
@Slf4j
final class ConnectionPropertyHandler<T> {
    private final Connection connection;
    private final ConnectionProperty property;
    private final ThrowableFunction<Connection, T, SQLException> getter;
    private final ThrowableBiConsumer<Connection, T, SQLException> setter;

    private T value;
    private boolean isInitialized = false;

    ConnectionPropertyHandler(Connection connection,
                              ConnectionProperty property,
                              ThrowableFunction<Connection, T, SQLException> getter,
                              ThrowableBiConsumer<Connection, T, SQLException> setter) {
        this.connection = connection;
        this.property = property;
        this.getter = getter;
        this.setter = setter;
    }

    void save() {
        try {
            value = getter.apply(connection);
            log.debug("Saving connection property {}: {}", property.getDisplayName(), value);
            isInitialized = true;
        } catch (SQLException e) {
            throw new SynchroTaskException(e);
        }
    }

    void restore() {
        if (!isInitialized) throw new IllegalStateException("Property has not been saved yet");
        log.debug("Restoring connection property {}: {}", property.getDisplayName(), value);
        try {
            setter.accept(connection, value);
        } catch (SQLException e) {
            throw new SynchroTaskException(e);
        }
    }
}
