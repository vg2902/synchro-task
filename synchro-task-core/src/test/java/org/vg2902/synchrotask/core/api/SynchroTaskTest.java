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

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.vg2902.synchrotask.core.api.SynchroTask.SynchroTaskBuilder;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SynchroTaskTest {

    private static final Runnable noop = () -> {};

    @Test
    public void buildsSynchroTaskFromSupplier() {
        SynchroTask<String> synchroTask = SynchroTask
                .from(() -> "foo")
                .withName("bar")
                .withId(42)
                .onLock(CollisionStrategy.WAIT)
                .build();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(synchroTask.getTaskName()).isEqualTo("bar");
        assertions.assertThat(synchroTask.getTaskId()).isEqualTo(42);
        assertions.assertThat(synchroTask.getCollisionStrategy()).isEqualTo(CollisionStrategy.WAIT);
        assertions.assertThat(synchroTask.execute()).isEqualTo("foo");
        assertions.assertAll();
    }

    @Test
    public void buildsSynchroTaskFromRunnable() {
        boolean[] sideEffectFlag = new boolean[]{false};

        SynchroTask<Void> synchroTask = SynchroTask
                .from(() -> {
                    sideEffectFlag[0] = true;
                })
                .withName("bar")
                .withId(42)
                .onLock(CollisionStrategy.THROW)
                .build();

        synchroTask.execute();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(synchroTask.getTaskName()).isEqualTo("bar");
        assertions.assertThat(synchroTask.getTaskId()).isEqualTo(42);
        assertions.assertThat(synchroTask.getCollisionStrategy()).isEqualTo(CollisionStrategy.THROW);
        assertions.assertThat(sideEffectFlag[0]).isTrue();
        assertions.assertAll();
    }

    @Test
    public void buildsSynchroTaskWithCollisionStrategyWaitByDefault() {
        SynchroTask<String> synchroTask = SynchroTask
                .from(() -> "foo")
                .withName("bar")
                .withId(42)
                .build();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(synchroTask.getTaskName()).isEqualTo("bar");
        assertions.assertThat(synchroTask.getTaskId()).isEqualTo(42);
        assertions.assertThat(synchroTask.getCollisionStrategy()).isEqualTo(CollisionStrategy.WAIT);
        assertions.assertThat(synchroTask.execute()).isEqualTo("foo");
        assertions.assertAll();
    }

    @Test
    public void doesNotAcceptNullRunnable() {
        assertThatThrownBy(() -> SynchroTask.from((Runnable) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullSupplier() {
        assertThatThrownBy(() -> SynchroTask.from((Supplier<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullTaskName() {
        SynchroTaskBuilder<Void> builder = SynchroTask.from(noop);
        assertThatThrownBy(() -> builder.withName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptMissingTaskName() {
        SynchroTaskBuilder<Void> builder = SynchroTask
                .from(noop)
                .withId(42)
                .onLock(CollisionStrategy.THROW);

        assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullTaskId() {
        SynchroTaskBuilder<Void> builder = SynchroTask.from(noop);
        assertThatThrownBy(() -> builder.withId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptMissingTaskId() {
        SynchroTaskBuilder<Void> builder = SynchroTask
                .from(noop)
                .withName("foo")
                .onLock(CollisionStrategy.THROW);

        assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullCollisionStrategy() {
        SynchroTaskBuilder<Void> builder = SynchroTask.from(noop);
        assertThatThrownBy(() -> builder.onLock(null)).isInstanceOf(NullPointerException.class);
    }
}