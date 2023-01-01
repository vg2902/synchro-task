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
package org.vg2902.synchrotask.redis;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.redisson.api.RLock;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.integration.AbstractSynchroTaskServiceIT;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static org.vg2902.synchrotask.redis.RedisResource.redisConnection;
import static org.vg2902.synchrotask.redis.RedisResource.redissonClient;

/**
 * {@link AbstractSynchroTaskServiceIT} implementation for Redis. During build, this test class is meant
 * to be executed as part of {@link RedisTestDocker} suite.
 */
@Slf4j
public class SynchroTaskRedisServiceIT extends AbstractSynchroTaskServiceIT {

    @BeforeClass
    public static void init() throws IOException {
        RedisResource.init();
    }

    protected Map<SynchroTask<?>, RLock> taskToLockMap = new ConcurrentHashMap<>();
    protected Map<SynchroTask<?>, Long> taskToThreadIdMap = new ConcurrentHashMap<>();
    private SynchroTaskRedisService synchroTaskService;

    @Before
    public void beforeTest() {
        synchroTaskService = SynchroTaskRedisService
                .from(RedisResource.redissonClient)
                .withInterceptor((task, rLock) -> {
                    taskToLockMap.put(task, rLock);
                    long id = Thread.currentThread().getId();
                    taskToThreadIdMap.put(task, id);
                    log.info("Thread: {}", id);
                })
                .build();
    }

    @After
    public void afterTest() {
        taskToThreadIdMap.clear();

    }

    @Override
    public SynchroTaskRedisService getSynchroTaskService() {
        return this.synchroTaskService;
    }

    @Override
    public boolean isBlocking(SynchroTask<?> blockedTask, SynchroTask<?> blockingTask) {
        String clientId = redissonClient.getId();

        Long blockingThreadId = taskToThreadIdMap.get(blockingTask);
        Long blockedThreadId = taskToThreadIdMap.get(blockedTask);
        RLock blockingRlock = taskToLockMap.get(blockingTask);
        RLock blockedRlock = taskToLockMap.get(blockedTask);

        String blockingRlockName = blockingRlock.getName();
        String blockedRlockName = blockedRlock.getName();

        if (!Objects.equals(blockingRlockName, blockedRlockName))
            return false;

        Set<String> blockingEntries = redisConnection
                .sync(StringCodec.INSTANCE, RedisCommands.HKEYS, blockingRlockName);

        String blockingEntryThreadId = getThreadIdFromEntries(clientId, blockingEntries);
        if (blockingEntryThreadId == null)
            return false;

        List<Object> blockedEntries = redisConnection
                .sync(RedisCommands.LRANGE, "redisson_lock_queue:{" + blockedRlockName + "}", 0, 1);

        String blockedEntryThreadId = getThreadIdFromEntries(clientId, blockedEntries);
        if (blockedEntryThreadId == null)
            return false;

        return Objects.equals(blockingEntryThreadId, String.valueOf(blockingThreadId)) &&
                Objects.equals(blockedEntryThreadId, String.valueOf(blockedThreadId));
    }

    private String getThreadIdFromEntries(String clientId, Collection<?> entries) {
        return ofNullable(entries)
                .orElse(emptySet())
                .stream()
                .map(e -> getThreadIdFromEntry(clientId, e))
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }

    private String getThreadIdFromEntry(String clientId, Object entry) {
        String[] tokens = entry.toString().split(":", 2);

        if (tokens.length != 2 || !Objects.equals(clientId, tokens[0]))
            return null;

        return tokens[1];
    }
}
