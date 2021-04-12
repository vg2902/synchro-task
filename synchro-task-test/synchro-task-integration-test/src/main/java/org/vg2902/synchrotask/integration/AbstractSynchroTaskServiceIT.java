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
package org.vg2902.synchrotask.integration;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTaskService;
import org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.with;

/**
 * Platform-agnostic integration test suite
 */
public abstract class AbstractSynchroTaskServiceIT {

    private static final long WAITING_SECONDS = 5;

    public abstract SynchroTaskService getSynchroTaskService();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public abstract boolean isBlocking(SynchroTask<?> blockedTask, SynchroTask<?> blockingTask) throws SQLException;

    @Test
    public void throwingTaskCompletes() {
        SynchroTaskService service = getSynchroTaskService();

        SynchroTask<Integer> synchroTask = SynchroTask
                .from(() -> 1)
                .withName("Throwing")
                .withId("1")
                .onLock(CollisionStrategy.THROW)
                .build();

        assertThat(service.run(synchroTask)).isEqualTo(1);
    }

    @Test
    public void throwingTaskThrowsCollisionExceptionIfSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withName("Throwing")
                .withId("1")
                .onLock(CollisionStrategy.THROW)
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 2)
                .withName("Throwing")
                .withId("1")
                .onLock(CollisionStrategy.THROW)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThatThrownBy(() -> service.run(synchroTask2)).isInstanceOf(SynchroTaskCollisionException.class);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);

        assertions.assertThat(service.run(synchroTask2)).isEqualTo(2);
        assertions.assertAll();
    }

    @Test
    public void throwingTaskCompletesIfNoSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withName("Throwing")
                .withId("1")
                .onLock(CollisionStrategy.THROW)
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 2)
                .withName("Throwing")
                .withId("2")
                .onLock(CollisionStrategy.THROW)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isEqualTo(2);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);
        assertions.assertAll();
    }

    @Test
    public void waitingTaskCompletes() {
        SynchroTaskService service = getSynchroTaskService();

        SynchroTask<Integer> synchroTask = SynchroTask
                .from(() -> 1)
                .withName("Waiting")
                .withId("1")
                .onLock(CollisionStrategy.WAIT)
                .build();

        assertThat(service.run(synchroTask)).isEqualTo(1);
    }

    @Test
    public void waitingTaskWaitsIfSameTaskIsRunning() throws ExecutionException, InterruptedException {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier1 = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier1)
                .withName("Waiting")
                .withId("1")
                .onLock(CollisionStrategy.WAIT)
                .build();

        Future<Integer> future1 = executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier1::isAwaiting);


        WaitingSupplier<Integer> waitingSupplier2 = new WaitingSupplier<>(2);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(waitingSupplier2)
                .withName("Waiting")
                .withId("1")
                .onLock(CollisionStrategy.WAIT)
                .build();

        Future<Integer> future2 = executor.submit(() -> service.run(synchroTask2));

        with().pollInterval(200, TimeUnit.MILLISECONDS).await()
                .atMost(WAITING_SECONDS, TimeUnit.SECONDS)
                .until(() -> isBlocking(synchroTask2, synchroTask1));

        waitingSupplier1.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier1::isTerminated);
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier2::isAwaiting);

        waitingSupplier2.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier2::isTerminated);

        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(future1.get()).isEqualTo(1);
        assertions.assertThat(future2.get()).isEqualTo(2);

        assertions.assertAll();
    }

    @Test
    public void waitingTaskCompletesIfNoSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withName("Waiting")
                .withId("1")
                .onLock(CollisionStrategy.WAIT)
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 2)
                .withName("Waiting")
                .withId("2")
                .onLock(CollisionStrategy.WAIT)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isEqualTo(2);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);
        assertions.assertAll();
    }

    @Test
    public void returningTaskCompletes() {
        SynchroTaskService service = getSynchroTaskService();

        SynchroTask<Integer> synchroTask = SynchroTask
                .from(() -> 1)
                .withName("Returning")
                .withId("1")
                .onLock(CollisionStrategy.RETURN)
                .build();

        assertThat(service.run(synchroTask)).isEqualTo(1);
    }

    @Test
    public void returningTaskReturnsNullIfSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withName("Returning")
                .withId("1")
                .onLock(CollisionStrategy.RETURN)
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 2)
                .withName("Returning")
                .withId("1")
                .onLock(CollisionStrategy.RETURN)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isNull();

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);

        assertions.assertThat(service.run(synchroTask2)).isEqualTo(2);
        assertions.assertAll();
    }

    @Test
    public void returningTaskCompletesIfNoSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withName("Returning")
                .withId("1")
                .onLock(CollisionStrategy.RETURN)
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 2)
                .withName("Returning")
                .withId("2")
                .onLock(CollisionStrategy.RETURN)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isEqualTo(2);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);
        assertions.assertAll();
    }

    private static class WaitingSupplier<T> implements Supplier<T> {

        private final T result;
        private final Phaser phaser = new Phaser(1);

        public WaitingSupplier(T result) {
            this.result = result;
        }

        @Override
        public T get() {
            phaser.register();
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndDeregister();

            return result;
        }

        public void proceed() {
            this.phaser.arriveAndDeregister();
        }

        public boolean isAwaiting() {
            return this.phaser.getArrivedParties() > 0;
        }

        public boolean isTerminated() {
            return this.phaser.isTerminated();
        }
    }

}
