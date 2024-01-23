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
package org.vg2902.synchrotask.core.api;

import org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

/**
 * A service for running {@link SynchroTask} instances.
 */
public interface SynchroTaskService {

    /**
     * Executes the given <b>task</b> with respect to its timeout settings.
     *
     * @param task {@link SynchroTask} instance
     * @param <T>  <b>task</b> return type
     * @return <b>task</b> return value
     * @throws SynchroTaskCollisionException is thrown when the lock timeout of a task with
     *                                       {@link SynchroTask.SynchroTaskBuilder#throwExceptionAfterTimeout(boolean)}
     *                                       set to <b>true</b> is expired
     * @throws SynchroTaskException          in case of any unhandled exception occurred during {@link SynchroTask} execution.
     * @see LockTimeout
     * @see SynchroTask
     */
    <T> T run(SynchroTask<T> task);
}
