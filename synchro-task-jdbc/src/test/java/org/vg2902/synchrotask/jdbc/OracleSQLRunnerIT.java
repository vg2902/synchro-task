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

import org.junit.BeforeClass;

import java.io.IOException;

/**
 * {@link AbstractSQLRunnerIT} implementation for Oracle. During build, this test class is meant to be executed
 * as part of {@link OracleTestDocker} suite.
 */
public class OracleSQLRunnerIT extends AbstractSQLRunnerIT implements OracleDatabaseIT {

    @BeforeClass
    public static void init() throws IOException {
        OracleResource.init();
    }
}
