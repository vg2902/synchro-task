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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTaskService;
import org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

/**
 * {@link SynchroTaskService} implementation which uses <a href="https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers">Redisson locks</a>
 * to keep track of running tasks and ensure synchronization.
 *
 * @see SynchroTask
 * @see SynchroTaskService
 */
@Getter
@Slf4j
public final class SynchroTaskRedisService implements SynchroTaskService {

    private final RedissonClient redissonClient;
    private final BiConsumer<SynchroTask<?>, RLock> interceptor;

    public SynchroTaskRedisService(RedissonClient redissonClient, BiConsumer<SynchroTask<?>, RLock> interceptor) {
        this.redissonClient = requireNonNull(redissonClient);
        this.interceptor = interceptor;
    }

    public static SynchroTaskRedisServiceBuilder from(RedissonClient redissonClient) {
        return SynchroTaskRedisServiceBuilder.from(redissonClient);
    }

    /**
     * Executes the given <b>task</b> with respect to its timeout settings using
     * <a href="https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers">Redisson locks</a>
     * to keep track of running tasks and ensure synchronization.
     *
     * @param task {@link SynchroTask} instance
     * @param <T>  <b>task</b> return type
     * @return <b>task</b> return value
     * @throws SynchroTaskCollisionException is thrown when the lock timeout of a task with
     *                                       {@link SynchroTask.SynchroTaskBuilder#throwExceptionAfterTimeout(boolean)}
     *                                       set to <b>true</b> is expired
     * @throws SynchroTaskException          in case of any unhandled exception occurred during {@link SynchroTask} execution
     */
    @Override
    public <T> T run(SynchroTask<T> task) {
        T result;

        try (SynchroTaskRedisLockManager lockManager = new SynchroTaskRedisLockManager(task, redissonClient)) {
            try {
                if (interceptor != null)
                    interceptor.accept(task, lockManager.getRLockInstance());

                if (!lockManager.lock()) {
                    log.debug("Cannot get lock for {}", task);

                    if (task.isThrowExceptionAfterTimeout())
                        throw new SynchroTaskCollisionException(task);
                    else
                        return null;
                }

                result = task.execute();
            } catch (SynchroTaskException e) {
                throw e;
            } catch (Exception e) {
                log.error("Exception occurred while executing {}", task);
                throw new SynchroTaskException(e);
            } finally {
                try {
                    lockManager.unlock();
                } catch (Exception e) {
                    log.error("Exception occurred while unlocking {}", task);
                }
            }
        }

        return result;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SynchroTaskRedisServiceBuilder {

        private RedissonClient redissonClient;
        private BiConsumer<SynchroTask<?>, RLock> interceptor;

        public static SynchroTaskRedisServiceBuilder from(RedissonClient redissonClient) {
            SynchroTaskRedisServiceBuilder builder = new SynchroTaskRedisServiceBuilder();
            builder.redissonClient = requireNonNull(redissonClient);
            return builder;
        }

        public SynchroTaskRedisServiceBuilder withInterceptor(BiConsumer<SynchroTask<?>, RLock> interceptor) {
            this.interceptor = requireNonNull(interceptor);
            return this;
        }

        public SynchroTaskRedisService build() {
            return new SynchroTaskRedisService(redissonClient, interceptor);
        }
    }
}
