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
package org.vg2902.synchrotask.jdbc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTask;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.RETURN;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.THROW;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

@RunWith(MockitoJUnitRunner.class)
public class LockManagerTest {

    @Mock
    private SQLRunner sqlRunner;

    @Test
    public void locksSuccessfully() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doReturn(true).when(sqlRunner).selectForUpdate(anyBoolean());

        LockManager lockManager = new LockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void locksSuccessfullyIfEntryAlreadyExists() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).insert();
        doReturn(true).when(sqlRunner).isDuplicateKey(e);
        doReturn(true).when(sqlRunner).selectForUpdate(anyBoolean());

        LockManager lockManager = new LockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void cannotLockIfAlreadyLocked() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).selectForUpdate(anyBoolean());
        doReturn(true).when(sqlRunner).isCannotAcquireLock(e);

        LockManager lockManager = new LockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isFalse();
    }

    @Test
    public void rethrowsIrrelevantExceptionsDuringEntryCreation() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).insert();
        doReturn(false).when(sqlRunner).isDuplicateKey(e);

        LockManager lockManager = new LockManager(sqlRunner);
        assertThatThrownBy(lockManager::lock).isEqualTo(e);
    }

    @Test
    public void rethrowsIrrelevantExceptionsDuringLocking() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).selectForUpdate(anyBoolean());
        doReturn(false).when(sqlRunner).isCannotAcquireLock(e);

        LockManager lockManager = new LockManager(sqlRunner);
        assertThatThrownBy(lockManager::lock).isEqualTo(e);
    }

    @Test
    public void retriesIfLockEntryIsStolenBetweenCreationAndLocking() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        when(sqlRunner.selectForUpdate(anyBoolean()))
                .thenReturn(false)
                .thenReturn(true);

        LockManager lockManager = new LockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void recognizesWaitingTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(WAIT), false);
    }

    @Test
    public void recognizesThrowingTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(THROW), true);
    }

    @Test
    public void recognizesReturningTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(RETURN), true);
    }

    private void handlesTaskCollisionStrategy(SynchroTask<?> synchroTask, boolean noWait) throws SQLException {
        doReturn(synchroTask).when(sqlRunner).getTask();
        doReturn(true).when(sqlRunner).selectForUpdate(noWait);

        LockManager lockManager = new LockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    private SynchroTask<?> getDummyTask() {
        return getDummyTask(WAIT);
    }

    private SynchroTask<?> getDummyTask(CollisionStrategy collisionStrategy) {
        return SynchroTask
                .from(() -> {})
                .withName("DummyTask")
                .withId("42")
                .onLock(collisionStrategy)
                .build();
    }
}
