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
package org.vg2902.synchrotask.spring;

import lombok.RequiredArgsConstructor;
import org.vg2902.synchrotask.core.api.LockTimeout;

import static org.vg2902.synchrotask.core.api.CollisionStrategy.RETURN;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.THROW;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

@RequiredArgsConstructor
public class TestRunner {

    @SynchroTask(onLock = WAIT)
    public String waitingTask(@TaskId String taskId) {
        return formatOutput("waitingTask", taskId);
    }

    @SynchroTask(onLock = THROW)
    public String throwingTask(@TaskId String taskId) {
        return formatOutput("throwingTask", taskId);
    }

    @SynchroTask(onLock = RETURN)
    public String returningTask(@TaskId String taskId) {
        return formatOutput("returningTask", taskId);
    }

    @SynchroTask
    public String defaultTask(@TaskId String taskId) {
        return formatOutput("defaultTask", taskId);
    }

    @SynchroTask
    public String failingTask(@TaskId String taskId) {
        throw new TestException();
    }

    @SynchroTask(serviceName = "service1")
    public String taskWithService1(@TaskId String taskId) {
        return formatOutput("defaultTaskWithService1", taskId);
    }

    @SynchroTask(serviceName = "service2")
    public String taskWithService2(@TaskId String taskId) {
        return formatOutput("defaultTaskWithService2", taskId);
    }

    @SynchroTask(lockTimeout = 0)
    public String noLockTimeoutTask(@TaskId String taskId) {
        return formatOutput("noLockTimeoutTask", taskId);
    }

    @SynchroTask(lockTimeout = LockTimeout.SYSTEM_DEFAULT_TIMEOUT)
    public String defaultLockTimeoutTask(@TaskId String taskId) {
        return formatOutput("defaultLockTimeoutTask", taskId);
    }

    @SynchroTask(lockTimeout = LockTimeout.MAX_SUPPORTED_TIMEOUT)
    public String maxSupportedLockTimeoutTask(@TaskId String taskId) {
        return formatOutput("maxSupportedLockTimeoutTask", taskId);
    }

    @SynchroTask(lockTimeout = LockTimeout.MAX_SUPPORTED_TIMEOUT, throwExceptionAfterTimeout = false)
    public String returningTimeoutTask(@TaskId String taskId) {
        return formatOutput("returningTimeoutTask", taskId);
    }

    @SynchroTask(lockTimeout = 20000)
    public String customLockTimeoutTask(@TaskId String taskId) {
        return formatOutput("customLockTimeoutTask", taskId);
    }

    private String formatOutput(String taskType, String taskId) {
        return String.join(":", taskType, taskId);
    }
}
