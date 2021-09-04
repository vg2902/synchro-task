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

import lombok.extern.slf4j.Slf4j;
import org.vg2902.synchrotask.core.api.SynchroTaskLockManager;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import java.sql.SQLException;

import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

/**
 * Implements lock/unlock functionality for a given {@link SynchroTask} using database as an underlying lock provider.
 * @see SynchroTask
 * @see SynchroTaskJdbcService
 */
@Slf4j
final class SynchroTaskJdbcLockManager implements SynchroTaskLockManager {

    enum LockResult {
        LOCK_RESULT_OK,
        LOCK_RESULT_NOT_FOUND,
        LOCK_RESULT_LOCKED_BY_ANOTHER_TASK
    }

    private final SQLRunner sqlRunner;
    private final SynchroTask<?> task;

    SynchroTaskJdbcLockManager(SQLRunner sqlRunner) {
        this.sqlRunner = sqlRunner;
        this.task = sqlRunner.getTask();
    }

    /**
     * Tries to lock given {@link SynchroTask} in two steps:
     * <ul>
     *     <li>creates a control row in the registry database table for the given {@link SynchroTask};
     *     if such a row already exists, it will be re-used;</li>
     *     <li>locks the row using database facilities;</li>
     * </ul>
     *
     * If for some reason the row has been deleted between the steps, the method will re-try.
     *
     * @return <b>true</b> if the lock is acquired successfully, <b>false</b> otherwise
     * @throws SynchroTaskException in case of any exception
     */
    @Override
    public boolean lock() throws SynchroTaskException {
        log.debug("Locking {}", task);

        try {
            while (true) {
                this.createLockEntry();
                LockResult lockResult = this.acquireLock();

                switch (lockResult) {
                    case LOCK_RESULT_OK:
                        log.debug("Lock is successfully acquired for {}", task);
                        return true;
                    case LOCK_RESULT_LOCKED_BY_ANOTHER_TASK:
                        log.debug("Lock entry for {} is being used by another task", task);
                        return false;
                    case LOCK_RESULT_NOT_FOUND:
                        log.debug("Lock entry for {} is not found. Re-trying.", task);
                        break;
                }
            }
        } catch (Exception e) {
            throw new SynchroTaskException(e);
        }
    }

    private void createLockEntry() throws SQLException {
        log.debug("Creating a lock entry for {}", task);

        try {
            sqlRunner.insert();
        } catch (SQLException e) {
            if (sqlRunner.isDuplicateKey(e)) {
                log.debug("Lock entry for {} already exists", task);
                return;
            }
            throw e;
        }

        log.debug("Lock entry for {} is successfully created", task);
    }

    private LockResult acquireLock() throws SQLException {
        log.debug("Acquiring lock for {}", task);

        try {
            boolean noWait = task.getCollisionStrategy() != WAIT;
            boolean isLocked = sqlRunner.selectForUpdate(noWait);

            if (!isLocked)
                return LockResult.LOCK_RESULT_NOT_FOUND;
        } catch (SQLException e) {
            if (sqlRunner.isCannotAcquireLock(e)) {
                return LockResult.LOCK_RESULT_LOCKED_BY_ANOTHER_TASK;
            }
            throw e;
        }

        return LockResult.LOCK_RESULT_OK;
    }

    /**
     * Unlocks previously locked task by deleting the control row from the registry table
     * @throws SynchroTaskException in case of any exception
     */
    @Override
    public void unlock() throws SynchroTaskException {
        log.debug("Releasing lock for {}", task);

        try {
            sqlRunner.delete();
        } catch (Exception e) {
            throw new SynchroTaskException(e);
        }
    }

    @Override
    public void close() {
        try {
            sqlRunner.close();
        } catch (Exception e) {
            log.error("Error closing lock manager", e);
        }
    }
}