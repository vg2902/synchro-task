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
package org.vg2902.synchrotask.spring;

import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTaskService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;
import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT_TIMEOUT;

/**
 * Enables <b>SynchroTask</b> semantics for the annotated method.
 * <p>
 * The framework will wrap the method invocations in individual {@link org.vg2902.synchrotask.core.api.SynchroTask}
 * instances and execute them with an eligible {@link SynchroTaskService} bean.
 * <p>
 * The annotated method must have a {@link TaskId}-annotated argument to designate the parameter that should be used
 * as the <b>taskId</b>:
 * <pre>
 * &#64;Component
 * public class SynchroTaskRunner {
 *
 *      &#64;SynchroTask
 *      public Integer defaultTask(@TaskId String taskId) {
 *          return 42;
 *      }
 *
 * }
 * </pre>
 * The object containing annotated methods is required to be a Spring bean.
 *
 * @see EnableSynchroTask
 * @see org.vg2902.synchrotask.core.api.SynchroTask
 * @see TaskId
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SynchroTask {
    /**
     * Optional name of the {@link SynchroTaskService} bean which should execute
     * {@link org.vg2902.synchrotask.core.api.SynchroTask} instances created from the annotated method
     *
     * @return the name of the {@link SynchroTaskService} bean
     */
    String serviceName() default "";

    /**
     * {@link CollisionStrategy} parameter of the tasks created from the annotated method
     *
     * @return {@link CollisionStrategy} value
     * @deprecated This class is a part of deprecated API and will be removed in the following releases.
     * Use {@link #lockTimeout()} parameter instead to control {@link SynchroTask} behaviour.
     * This value will be ignored if non-default {@link #lockTimeout()} is provided.
     */
    CollisionStrategy onLock() default WAIT;

    /**
     * @return Lock timeout in milliseconds
     * @see org.vg2902.synchrotask.core.api.LockTimeout
     */
    long lockTimeout() default SYSTEM_DEFAULT_TIMEOUT;

    /**
     * @return indicates whether the annotated method should throw an {@link org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException}
     * if execution is locked by another {@link org.vg2902.synchrotask.core.api.SynchroTask} instance
     * and the {@link #lockTimeout()} is expired.
     * <p>
     * When set to <b>false</b>, the locked method will return immediately with return value of null for non-void methods.
     */
    boolean throwExceptionAfterTimeout() default true;
}
