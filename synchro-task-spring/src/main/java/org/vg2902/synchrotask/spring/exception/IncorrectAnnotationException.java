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
package org.vg2902.synchrotask.spring.exception;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

/**
 * Indicates incorrect usage of {@link org.vg2902.synchrotask.spring.TaskId} annotation.
 *
 * @see org.vg2902.synchrotask.spring.SynchroTask
 */
@EqualsAndHashCode(callSuper = false)
@ToString
public final class IncorrectAnnotationException extends SynchroTaskException {

    private static final String MESSAGE_TEMPLATE = "Incorrect usage of @%1$s annotation in %2$s. Expected 1 annotated argument, but found %3$d";

    private final String annotation;
    private final String method;
    private final int count;

    public IncorrectAnnotationException(String annotation, String method, int count) {
        super();
        this.annotation = annotation;
        this.method = method;
        this.count = count;
    }

    @Override
    public String getMessage() {
        return String.format(MESSAGE_TEMPLATE, annotation, method, count);
    }
}
