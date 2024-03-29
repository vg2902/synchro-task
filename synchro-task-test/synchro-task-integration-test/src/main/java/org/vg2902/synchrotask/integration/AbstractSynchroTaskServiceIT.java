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
package org.vg2902.synchrotask.integration;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
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
@Slf4j
public abstract class AbstractSynchroTaskServiceIT {

    private static final long WAITING_SECONDS = 5;

    public abstract SynchroTaskService getSynchroTaskService();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public abstract boolean isBlocking(SynchroTask<?> blockedTask, SynchroTask<?> blockingTask) throws SQLException;

    @Test
    public void completes() {
        SynchroTaskService service = getSynchroTaskService();

        SynchroTask<Integer> synchroTask = SynchroTask
                .from(() -> 42)
                .withId("foo")
                .build();

        assertThat(service.run(synchroTask)).isEqualTo(42);
    }

    @Test
    public void throwsCollisionExceptionIfSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withId("foo")
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 42)
                .withId("foo")
                .withLockTimeout(0)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThatThrownBy(() -> service.run(synchroTask2)).isInstanceOf(SynchroTaskCollisionException.class);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);

        assertions.assertThat(service.run(synchroTask2)).isEqualTo(42);
        assertions.assertAll();
    }

    @Test
    public void returnsNullIfSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withId("foo")
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 42)
                .withId("foo")
                .withLockTimeout(0)
                .throwExceptionAfterTimeout(false)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isNull();
        assertions.assertThat(waitingSupplier.isAwaiting()).isTrue();

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);
        assertions.assertAll();
    }

    @Test
    public void completesIfNoSameTaskIsRunning() {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier)
                .withId("foo")
                .build();

        executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isAwaiting);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(() -> 42)
                .withId("bar")
                .withLockTimeout(0)
                .build();


        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.run(synchroTask2)).isEqualTo(42);

        waitingSupplier.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier::isTerminated);
        assertions.assertAll();
    }

    @Test
    public void waitsIndefinitelyIfSameTaskIsRunning() throws ExecutionException, InterruptedException {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier1 = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier1)
                .withId("foo")
                .build();

        Future<Integer> future1 = executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier1::isAwaiting);


        WaitingSupplier<Integer> waitingSupplier2 = new WaitingSupplier<>(2);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(waitingSupplier2)
                .withId("foo")
                .withMaxSupportedLockTimeout()
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
    public void waitsSpecifiedTimeIfSameTaskIsRunning() throws ExecutionException, InterruptedException {
        SynchroTaskService service = getSynchroTaskService();

        WaitingSupplier<Integer> waitingSupplier1 = new WaitingSupplier<>(1);

        SynchroTask<Integer> synchroTask1 = SynchroTask
                .from(waitingSupplier1)
                .withId("foo")
                .build();

        Future<Integer> future1 = executor.submit(() -> service.run(synchroTask1));
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier1::isAwaiting);


        WaitingSupplier<Integer> waitingSupplier2 = new WaitingSupplier<>(2);

        SynchroTask<Integer> synchroTask2 = SynchroTask
                .from(waitingSupplier2)
                .withId("foo")
                .withLockTimeout(3000L)
                .build();

        Future<Integer> future2 = executor.submit(() -> service.run(synchroTask2));

        with().pollInterval(200, TimeUnit.MILLISECONDS)
                .await()
                .atMost(WAITING_SECONDS, TimeUnit.SECONDS)
                .until(() -> isBlocking(synchroTask2, synchroTask1));

        with().pollInterval(1000, TimeUnit.MILLISECONDS)
                .await()
                .atLeast(3, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> !isBlocking(synchroTask2, synchroTask1));


        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThatThrownBy(future2::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(SynchroTaskCollisionException.class);

        waitingSupplier1.proceed();
        await().atMost(WAITING_SECONDS, TimeUnit.SECONDS).until(waitingSupplier1::isTerminated);

        assertions.assertThat(future1.get()).isEqualTo(1);
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
