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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.EnumMap;
import java.util.Map;

/**
 * A data structure representing temporary storage for certain {@link Connection} attributes.
 * Used by {@link SynchroTaskJdbcService} to restore initial state of the connections
 * before returning them back to the {@link DataSource}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
class ConnectionState {
    private boolean autoCommit;
    private int transactionIsolation;
    private Map<ConnectionProperty, Object> properties = new EnumMap<>(ConnectionProperty.class);

    enum ConnectionProperty {
        LOCK_TIMEOUT
    }
}
