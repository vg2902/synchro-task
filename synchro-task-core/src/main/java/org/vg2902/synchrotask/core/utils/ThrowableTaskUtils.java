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
import org.vg2902.synchrotask.core.api.SynchroTask;

import java.util.function.Supplier;

import static org.vg2902.synchrotask.core.utils.ThrowableTaskResult.exception;
import static org.vg2902.synchrotask.core.utils.ThrowableTaskResult.result;

/**
 * Utility class with handy methods that help to encapsulate excessive boilerplate code when {@link SynchroTask}
 * is based on a routine which may throw a checked exception.
 *
 * @see ThrowableSupplier
 * @see ThrowableTaskResult
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThrowableTaskUtils {

    /**
     * Converts the given {@link ThrowableSupplier} to a {@link Supplier} which returns {@link ThrowableTaskResult}.
     *
     * @param supplier a {@link ThrowableSupplier}
     * @param <T> <b>supplier</b> return type
     * @return a {@link Supplier} which returns a {@link ThrowableTaskResult} constructed from the given <b>supplier</b> outcome
     */
    public static <T> Supplier<ThrowableTaskResult<T>> getSupplier(ThrowableSupplier<T, Throwable> supplier) {
        return () -> get(supplier);
    }

    /**
     * Executes the given <b>supplier</b> and returns a {@link ThrowableTaskResult} constructed from the outcome.
     *
     * @param supplier a {@link ThrowableSupplier}
     * @param <T> <b>supplier</b> return type
     * @return {@link ThrowableTaskResult} constructed from the given <b>supplier</b> outcome
     */
    public static <T> ThrowableTaskResult<T> get(ThrowableSupplier<T, Throwable> supplier) {
        try {
            return result(supplier.get());
        } catch (Throwable throwable) {
            return exception(throwable);
        }
    }

}
