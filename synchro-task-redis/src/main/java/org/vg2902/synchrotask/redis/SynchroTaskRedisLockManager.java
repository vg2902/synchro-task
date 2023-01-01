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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.vg2902.synchrotask.core.api.LockTimeout;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTaskLockManager;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import java.util.concurrent.TimeUnit;

/**
 * Implements lock/unlock functionality for a given {@link SynchroTask} using Redis instance as an underlying lock provider
 * with the help of <a href="https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers">Redisson locks</a>
 *
 * @see SynchroTask
 * @see SynchroTaskRedisService
 */
@Slf4j
final class SynchroTaskRedisLockManager implements SynchroTaskLockManager {

    private final SynchroTask<?> task;
    @Getter
    private final RLock rLockInstance;

    public SynchroTaskRedisLockManager(SynchroTask<?> task, RedissonClient redissonClient) {
        this.task = task;
        this.rLockInstance = redissonClient.getFairLock(task.getTaskId());
    }

    /**
     * Tries to lock given {@link SynchroTask} using Redisson locking facilities.
     *
     * @return <b>true</b> if the lock is acquired successfully, <b>false</b> otherwise
     * @throws SynchroTaskException in case of any exception
     */
    @Override
    public boolean lock() throws SynchroTaskException {
        log.debug("Locking {}", task);

        LockTimeout lockTimeout = task.getLockTimeout();

        boolean lockResult;

        try {
            if (lockTimeout == LockTimeout.MAX_SUPPORTED || lockTimeout == LockTimeout.SYSTEM_DEFAULT) {
                rLockInstance.lock();
                lockResult = true;
            }
            else {
                lockResult = rLockInstance.tryLock(lockTimeout.getValueInMillis(), TimeUnit.MILLISECONDS);
            }

            if (lockResult)
                log.debug("Lock is successfully acquired for {}", task);
            else
                log.debug("Lock for {} is being used by another task", task);

        } catch (Exception e) {
            throw new SynchroTaskException(e);
        }

        return lockResult;
    }

    /**
     * Unlocks a previously locked task
     *
     * @throws SynchroTaskException in case of any exception
     */
    @Override
    public void unlock() throws SynchroTaskException {
        log.debug("Releasing lock for {}", task);

        try {
            rLockInstance.unlock();
        } catch (Exception e) {
            throw new SynchroTaskException(e);
        }
    }

    @Override
    public void close() {
    }
}