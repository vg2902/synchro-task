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
package org.vg2902.synchrotask.core.utils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Objects.requireNonNull;

/**
 * Represents the result of an operation which either completes successfully or fails with an exception.
 *
 * @param <T> operation result type
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThrowableTaskResult<T> {

    private final T result;
    private final Throwable exception;

    /**
     * Static factory method to construct a {@link ThrowableTaskResult} representing a successful operation.
     *
     * @param result result value
     * @param <T> result type
     * @return {@link ThrowableTaskResult} instance with the given result value
     */
    public static <T> ThrowableTaskResult<T> result(T result) {
        return new ThrowableTaskResult<>(result, null);
    }

    /**
     * Static factory method to construct a {@link ThrowableTaskResult} representing an operation that failed to complete.
     *
     * @param exception exception which caused the failure
     * @param <T> expected result type
     * @return {@link ThrowableTaskResult} instance with the given exception
     * @throws NullPointerException if <b>exception</b> argument is null
     */
    public static <T> ThrowableTaskResult<T> exception(Throwable exception) {
        return new ThrowableTaskResult<>(null, requireNonNull(exception));
    }

    /**
     * Checks whether this instance represents a successful operation result.
     *
     * @return <b>true</b> if this {@link ThrowableTaskResult} was created with {@link #result(Object)} factory method, <b>false</b> otherwise
     */
    public boolean isSuccessful() {
        return exception == null;
    }

    /**
     * Checks whether this instance represents a failed operation result.
     *
     * @return <b>true</b> if this {@link ThrowableTaskResult} was created with {@link #exception(Throwable)} factory method, <b>false</b> otherwise
     */
    public boolean isFailed() {
        return !isSuccessful();
    }

}
