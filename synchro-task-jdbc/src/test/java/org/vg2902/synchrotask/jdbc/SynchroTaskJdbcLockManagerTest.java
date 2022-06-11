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
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.RETURN;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.THROW;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;
import static org.vg2902.synchrotask.jdbc.EntryCreationResult.CREATION_RESULT_ALREADY_EXISTS;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_NOT_FOUND;
import static org.vg2902.synchrotask.jdbc.EntryLockResult.LOCK_RESULT_OK;

@RunWith(MockitoJUnitRunner.class)
public class SynchroTaskJdbcLockManagerTest {

    @Mock
    private AbstractSQLRunner<?> sqlRunner;

    @Test
    public void locksSuccessfully() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doReturn(LOCK_RESULT_OK).when(sqlRunner).acquireLock();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void locksSuccessfullyIfEntryAlreadyExists() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doReturn(CREATION_RESULT_ALREADY_EXISTS).when(sqlRunner).createLockEntry();
        doReturn(LOCK_RESULT_OK).when(sqlRunner).acquireLock();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void cannotLockIfAlreadyLocked() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doReturn(LOCK_RESULT_LOCKED_BY_ANOTHER_TASK).when(sqlRunner).acquireLock();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isFalse();
    }

    @Test
    public void rethrowsIrrelevantExceptionsDuringEntryCreation() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).createLockEntry();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        assertThatThrownBy(lockManager::lock)
                .isExactlyInstanceOf(SynchroTaskException.class)
                .hasCause(e);
    }

    @Test
    public void rethrowsIrrelevantExceptionsDuringLocking() throws SQLException {
        SQLException e = new SQLException();

        doReturn(getDummyTask()).when(sqlRunner).getTask();
        doThrow(e).when(sqlRunner).acquireLock();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        assertThatThrownBy(lockManager::lock)
                .isExactlyInstanceOf(SynchroTaskException.class)
                .hasCause(e);
    }

    @Test
    public void retriesIfLockEntryIsStolenBetweenCreationAndLocking() throws SQLException {
        doReturn(getDummyTask()).when(sqlRunner).getTask();
        when(sqlRunner.acquireLock())
                .thenReturn(LOCK_RESULT_NOT_FOUND)
                .thenReturn(LOCK_RESULT_OK);

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    @Test
    public void recognizesWaitingTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(WAIT));
    }

    @Test
    public void recognizesThrowingTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(THROW));
    }

    @Test
    public void recognizesReturningTasks() throws SQLException {
        handlesTaskCollisionStrategy(getDummyTask(RETURN));
    }

    private void handlesTaskCollisionStrategy(SynchroTask<?> synchroTask) throws SQLException {
        doReturn(synchroTask).when(sqlRunner).getTask();
        doReturn(LOCK_RESULT_OK).when(sqlRunner).acquireLock();

        SynchroTaskJdbcLockManager lockManager = new SynchroTaskJdbcLockManager(sqlRunner);
        boolean isLocked = lockManager.lock();

        assertThat(isLocked).isTrue();
    }

    private SynchroTask<?> getDummyTask() {
        return getDummyTask(WAIT);
    }

    private SynchroTask<?> getDummyTask(CollisionStrategy collisionStrategy) {
        return SynchroTask
                .from(() -> {})
                .withId("DummyTask")
                .onLock(collisionStrategy)
                .build();
    }
}
