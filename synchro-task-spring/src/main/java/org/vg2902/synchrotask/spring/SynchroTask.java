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

import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTaskService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.vg2902.synchrotask.core.api.CollisionStrategy.WAIT;

/**
 * Enables <b>SynchroTask</b> semantics for the annotated method.
 * <p>
 * The framework will wrap the method invocations in individual {@link org.vg2902.synchrotask.core.api.SynchroTask}
 * instances and execute them with an eligible {@link SynchroTaskService} bean.
 * <p>
 * The annotated method must have two arguments with {@link TaskName} and {@link TaskId} annotations respectively to designate the
 * parameters that should be used as the <b>taskName</b> and <b>taskId</b>:
 * <pre>
 * &#64;Component
 * public class SynchroTaskRunner {
 *
 *      &#64;SynchroTask
 *      public Integer defaultTask(@TaskName String taskName, @TaskId long taskId) {
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
 * @see TaskName
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
     */
    CollisionStrategy onLock() default WAIT;
}
