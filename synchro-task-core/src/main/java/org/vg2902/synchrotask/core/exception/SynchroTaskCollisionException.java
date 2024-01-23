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
package org.vg2902.synchrotask.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTaskService;

/**
 * Indicates a <b>collision</b>, i.e., an attempt to run a {@link SynchroTask} while another instance with the same
 * <b>taskId</b> is still running.
 * @see SynchroTaskService#run(SynchroTask)
 */
@AllArgsConstructor
@Getter
public final class SynchroTaskCollisionException extends SynchroTaskException {
    private final SynchroTask<?> synchroTask;
}
