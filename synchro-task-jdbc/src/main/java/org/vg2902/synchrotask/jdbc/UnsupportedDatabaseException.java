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

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

/**
 * Indicates an attempt to initialize {@link SynchroTaskJdbcService} based on an unsupported database.
 * Supported databases are listed in {@link SynchroTaskDatabaseSupport} enum.
 */
@AllArgsConstructor
@Getter
public final class UnsupportedDatabaseException extends SynchroTaskException {
    private final String databaseName;

    @Override
    public String toString() {
        return "Unsupported database: " + databaseName;
    }
}
