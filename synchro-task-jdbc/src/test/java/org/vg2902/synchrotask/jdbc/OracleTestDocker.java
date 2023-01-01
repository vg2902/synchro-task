/*
 * Copyright 2021-2023 vg2902.org
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import java.io.IOException;

/**
 * Integration test suite for Oracle
 */
@RunWith(Suite.class)
@SuiteClasses({OracleSQLRunnerIT.class, OracleSynchroTaskJdbcServiceIT.class})
public class OracleTestDocker {

    @BeforeClass
    public static void init() throws IOException {
        OracleResource.init();
    }

    @AfterClass
    public static void shutdown() {
        OracleResource.shutdown();
    }
}
