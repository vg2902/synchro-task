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
 * {@link #collisionStrategy} attribute can be used to control how collisions caused by this SynchroTask
 * should be handled, see {@link CollisionStrategy} for available options.
 * If not specified explicitly during initialization, it is defaulted to {@link CollisionStrategy#WAIT}.
 * <p>
 * SynchroTask workload is provided in the form of {@link Supplier}, or {@link Runnable} for the tasks with no return value.
 * <p>
 * There are static builder methods to construct {@link SynchroTask} objects:
 *
 * <pre>
 *    // Throwing task with name <b>bar</b> and id <b>42</b> with no return value
 *
 *    SynchroTask&lt;Void&gt; synchroTask = SynchroTask
 *            .from(() -> System.out.println("foo"))
 *            .withName("bar")
 *            .withId(42)
 *            .onLock(CollisionStrategy.THROW)
 *            .build();
 * </pre>
 * <pre>
 *    // Waiting task with name <b>bar</b> and id <b>42</b> with {@link String} return type
 *
 *    SynchroTask&lt;String&gt; synchroTask = SynchroTask
 *            .from(() -> "foo")
 *            .withName("bar")
 *            .withId(42)
 *            .onLock(CollisionStrategy.WAIT)
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
 * @see CollisionStrategy
 * @see SynchroTaskService
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class SynchroTask<T> {

    private SynchroTask(Supplier<T> task, Object taskName, Object taskId, CollisionStrategy collisionStrategy) {
        this.task = requireNonNull(task);
        this.taskName = requireNonNull(taskName);
        this.taskId = requireNonNull(taskId);
        this.collisionStrategy = requireNonNull(collisionStrategy);
    }

    private final Supplier<T> task;

    @ToString.Include
    private final Object taskName;

    @ToString.Include
    private final Object taskId;

    @ToString.Include
    private final CollisionStrategy collisionStrategy;

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
        private CollisionStrategy collisionStrategy = CollisionStrategy.WAIT;

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

        public SynchroTaskBuilder<T> onLock(CollisionStrategy collisionStrategy) {
            this.collisionStrategy = requireNonNull(collisionStrategy);
            return this;
        }

        public SynchroTask<T> build() {
            return new SynchroTask<>(task, taskName, taskId, collisionStrategy);
        }
    }
}
