/*
 * Copyright 2021-2023 vg2902.org
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

/**
 * Defines {@link SynchroTask} behaviour in case of collisions, i.e., when a task cannot start
 * because another {@link SynchroTask} instance with the same <b>taskId</b> is already executing.
 *
 * @deprecated This class is a part of deprecated API and will be removed in the following releases.
 * Use {@link LockTimeout} instead to control {@link SynchroTask} behaviour.
 */
@Deprecated
public enum CollisionStrategy {
    /**
     * {@link SynchroTask} throwing {@link SynchroTaskCollisionException}.
     */
    THROW,

    /**
     * {@link SynchroTask} waiting until the blocking instance completes.
     */
    WAIT,

    /**
     * {@link SynchroTask} returning <b>null</b> without actually executing.
     */
    RETURN
}
