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
package org.vg2902.synchrotask.spring;

import lombok.RequiredArgsConstructor;
import org.vg2902.synchrotask.core.api.LockTimeout;

import static org.vg2902.synchrotask.core.api.CollisionStrategy.RETURN;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.THROW;
import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

@RequiredArgsConstructor
public class TestRunner {

    @SynchroTask(onLock = WAIT)
    public String waitingTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("waitingTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(onLock = THROW)
    public String throwingTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("throwingTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(onLock = RETURN)
    public String returningTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("returningTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask
    public String defaultTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("defaultTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask
    public String failingTask(@TaskName String taskName, @TaskId int taskId) {
        throw new TestException();
    }

    @SynchroTask(serviceName = "service1")
    public String taskWithService1(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("defaultTaskWithService1", taskName, String.valueOf(taskId));
    }

    @SynchroTask(serviceName = "service2")
    public String taskWithService2(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("defaultTaskWithService2", taskName, String.valueOf(taskId));
    }

    @SynchroTask(lockTimeout = 0)
    public String noLockTimeoutTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("noLockTimeoutTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(lockTimeout = LockTimeout.SYSTEM_DEFAULT_TIMEOUT)
    public String defaultLockTimeoutTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("defaultLockTimeoutTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(lockTimeout = LockTimeout.MAX_SUPPORTED_TIMEOUT)
    public String maxSupportedLockTimeoutTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("maxSupportedLockTimeoutTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(lockTimeout = LockTimeout.MAX_SUPPORTED_TIMEOUT, throwExceptionAfterTimeout = false)
    public String returningTimeoutTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("returningTimeoutTask", taskName, String.valueOf(taskId));
    }

    @SynchroTask(lockTimeout = 20000)
    public String customLockTimeoutTask(@TaskName String taskName, @TaskId int taskId) {
        return formatOutput("customLockTimeoutTask", taskName, String.valueOf(taskId));
    }

    private String formatOutput(String taskType, String taskName, String taskId) {
        return String.join(":", taskType, taskName, taskId);
    }
}
