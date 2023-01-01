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
package org.vg2902.synchrotask.core.utils;

import java.util.function.Function;

/**
 * Enhanced {@link Function} representing the operations that may throw an error or exception.
 *
 * @param <T> argument type
 * @param <R> result type
 * @param <E> exception type
 * @see Function
 */
@FunctionalInterface
public interface ThrowableFunction<T, R, E extends Throwable> {
    R apply(T t) throws E;
}
