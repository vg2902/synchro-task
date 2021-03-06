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
package org.vg2902.synchrotask.core.api;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Represents a unit of work which requires synchronization while executing.
 * <p>
 * Each SynchroTask is uniquely identified by a combination of {@link #taskName} and {@link #taskId}.
 * Once initiated and until completed, a SynchroTask instance will prevent other instances
 * with the same {@link #taskName} and {@link #taskId} from being launched in parallel.
 * An attempt to start such an instance will cause a <b>collision</b> and will be rejected.
 * In other words, {@link SynchroTask} instance <b>acquires</b>/<b>releases</b> a <b>lock</b>
 * upon start/completion respectively.
 * <p>
 * {@link #lockTimeout} attribute can be used to control how long a blocked task should wait if it is blocked.
 * When not specified, the task will be using {@link LockTimeout#SYSTEM_DEFAULT_TIMEOUT}. See {@link LockTimeout}
 * for more detail.
 * <p>
 * If the timeout is over and the task is still blocked, it can either throw
 * {@link org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException} or return null.
 * This behaviour can be switched using {@link #throwExceptionAfterTimeout} parameter, which default value is <b>true</b>
 * <p>
 * SynchroTask workload is provided in the form of {@link Supplier}, or {@link Runnable} for the tasks with no return value.
 * <p>
 * There are static builder methods to construct {@link SynchroTask} objects:
 *
 * <pre>
 *    // A task with name <b>bar</b> and id <b>42</b> with no return value, throwing the exception after 10s timeout
 *
 *    SynchroTask&lt;Void&gt; synchroTask = SynchroTask
 *            .from(() -&gt; System.out.println("foo"))
 *            .withName("bar")
 *            .withId(42)
 *            .withLockTimeout(10000)
 *            .throwExceptionAfterTimeout(true)
 *            .build();
 * </pre>
 * <pre>
 *    // A task with name <b>bar</b> and id <b>42</b> with {@link String} return type and the max timeout supported by the lock provider
 *
 *    SynchroTask&lt;String&gt; synchroTask = SynchroTask
 *            .from(() -&gt; "foo")
 *            .withName("bar")
 *            .withId(42)
 *            .withLockTimeout(LockTimeout.MAX_SUPPORTED)
 *            .build();
 * </pre>
 * <p>
 * All attributes passed to the builder must not be null, otherwise {@link SynchroTaskBuilder#build()}
 * will throw {@link NullPointerException}.
 * <p>
 * Although {@link #taskName} and {@link #taskId} can be objects of any type, all {@link SynchroTaskService}
 * implementations will internally convert them into {@link String} applying {@link String#valueOf(Object)}.
 * These {@link String} representations will be actually passed into external lock providers.
 *
 * @param <T> task return type
 * @see LockTimeout
 * @see SynchroTaskService
 */
@Getter
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class SynchroTask<T> {

    private SynchroTask(Supplier<T> task,
                        Object taskName,
                        Object taskId,
                        LockTimeout lockTimeout,
                        boolean throwExceptionAfterTimeout) {
        this.task = requireNonNull(task);
        this.taskName = requireNonNull(taskName);
        this.taskId = requireNonNull(taskId);
        this.lockTimeout = requireNonNull(lockTimeout);
        this.throwExceptionAfterTimeout = throwExceptionAfterTimeout;
    }

    private final Supplier<T> task;

    @ToString.Include
    private final Object taskName;

    @ToString.Include
    private final Object taskId;

    @ToString.Include
    private final LockTimeout lockTimeout;

    @ToString.Include
    private final boolean throwExceptionAfterTimeout;

    public T execute() {
        return task.get();
    }

    public static <T> SynchroTaskBuilder<T> from(Supplier<T> task) {
        return SynchroTaskBuilder.from(task);
    }

    public static SynchroTaskBuilder<Void> from(Runnable task) {
        return SynchroTaskBuilder.from(task);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SynchroTaskBuilder<T> {

        private Supplier<T> task;
        private Object taskName;
        private Object taskId;
        private LockTimeout lockTimeout = LockTimeout.SYSTEM_DEFAULT;
        private boolean throwExceptionAfterTimeout = true;

        public static <T> SynchroTaskBuilder<T> from(Supplier<T> task) {
            SynchroTaskBuilder<T> builder = new SynchroTaskBuilder<>();
            builder.task = requireNonNull(task);
            return builder;
        }

        public static <T> SynchroTaskBuilder<T> from(Runnable task) {
            SynchroTaskBuilder<T> builder = new SynchroTaskBuilder<>();
            requireNonNull(task);

            builder.task = () -> {
                task.run();
                return null;
            };

            return builder;
        }

        public SynchroTaskBuilder<T> withName(Object taskName) {
            this.taskName = requireNonNull(taskName);
            return this;
        }

        public SynchroTaskBuilder<T> withId(Object taskId) {
            this.taskId = requireNonNull(taskId);
            return this;
        }

        /**
         * @deprecated {@link CollisionStrategy} is a part of deprecated API and will be removed in the following releases.
         * Use {@link LockTimeout} instead to control {@link SynchroTask} behaviour.
         */
        @Deprecated
        public SynchroTaskBuilder<T> onLock(CollisionStrategy collisionStrategy) {
            mapCollisionStrategy(collisionStrategy);
            return this;
        }

        private void mapCollisionStrategy(CollisionStrategy collisionStrategy) {
            requireNonNull(collisionStrategy);

            switch (collisionStrategy) {
                case WAIT:
                    this.lockTimeout = LockTimeout.SYSTEM_DEFAULT;
                    this.throwExceptionAfterTimeout = true;
                    break;
                case RETURN:
                    this.lockTimeout = LockTimeout.of(0);
                    this.throwExceptionAfterTimeout = false;
                    break;
                case THROW:
                    this.lockTimeout = LockTimeout.of(0);
                    this.throwExceptionAfterTimeout = true;
                    break;
            }
        }

        public SynchroTaskBuilder<T> withDefaultLockTimeout() {
            this.lockTimeout = LockTimeout.SYSTEM_DEFAULT;
            return this;
        }

        public SynchroTaskBuilder<T> withMaxSupportedLockTimeout() {
            this.lockTimeout = LockTimeout.MAX_SUPPORTED;
            return this;
        }

        public SynchroTaskBuilder<T> withZeroLockTimeout() {
            this.lockTimeout = LockTimeout.of(0);
            return this;
        }

        public SynchroTaskBuilder<T> withLockTimeout(long lockTimeoutInMillis) {
            this.lockTimeout = LockTimeout.of(lockTimeoutInMillis);
            return this;
        }

        public SynchroTaskBuilder<T> withLockTimeout(LockTimeout lockTimeout) {
            this.lockTimeout = lockTimeout;
            return this;
        }

        public SynchroTaskBuilder<T> throwExceptionAfterTimeout(boolean throwExceptionAfterTimeout) {
            this.throwExceptionAfterTimeout = throwExceptionAfterTimeout;
            return this;
        }

        public SynchroTask<T> build() {
            return new SynchroTask<>(task, taskName, taskId, lockTimeout, throwExceptionAfterTimeout);
        }
    }
}
