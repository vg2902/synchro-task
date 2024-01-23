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

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.integration.AbstractSynchroTaskServiceIT;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-specific version of {@link AbstractSynchroTaskServiceIT}.
 * These tests require a real database instance to be up and running.
 */
@Slf4j
public abstract class AbstractSynchroTaskJdbcServiceIT extends AbstractSynchroTaskServiceIT implements DatabaseIT {

    protected Map<SynchroTask<?>, Long> taskToSessionIdMap = new ConcurrentHashMap<>();
    private SynchroTaskJdbcService synchroTaskService;

    @Before
    public void beforeTest() {
        synchroTaskService = SynchroTaskJdbcService
                .from(getDataSource())
                .withTableName(TABLE_NAME)
                .withInterceptor((task, connection) -> taskToSessionIdMap.put(task, getSessionId(connection)))
                .build();
    }

    @After
    public void afterTest() {
        taskToSessionIdMap.clear();
    }

    @Override
    public SynchroTaskJdbcService getSynchroTaskService() {
        return this.synchroTaskService;
    }

    @Override
    public boolean isBlocking(SynchroTask<?> blockedTask, SynchroTask<?> blockingTask) {
        Long blockedSessionId = taskToSessionIdMap.get(blockedTask);
        Long blockingSessionId = taskToSessionIdMap.get(blockingTask);

        return isDatabaseSessionBlocked(blockedSessionId, blockingSessionId);
    }
}
