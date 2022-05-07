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
package org.vg2902.synchrotask.core.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Timeout configuration for a {@link SynchroTask}.
 * <p>
 * Initialized by a non-negative argument representing a timeout in milliseconds.
 * <p>
 * There are two pre-defined values:
 * <p>
 * <ul>
 *     <li>{@link #MAX_SUPPORTED} tasks with this timeout will wait as long as it is possibly allowed by the
 *     given lock provider;</li>
 *     <li>{@link #SYSTEM_DEFAULT} tasks will not update timeout settings and will rely on the given lock
 *     provider defaults;</li>
 * </ul>
 * <p>
 * These special instances can be either accessed directly or obtained with the factory method {@link #of(long)} using
 * their numeric companion constants:
 * <p>
 * {@link #MAX_SUPPORTED_TIMEOUT}
 * <p>
 * {@link #SYSTEM_DEFAULT_TIMEOUT}
 * <p>
 */
@Getter
@EqualsAndHashCode
public final class LockTimeout {

    public static final long SYSTEM_DEFAULT_TIMEOUT = -2;
    public static final long MAX_SUPPORTED_TIMEOUT = -1;

    public static final LockTimeout SYSTEM_DEFAULT = new LockTimeout(SYSTEM_DEFAULT_TIMEOUT);
    public static final LockTimeout MAX_SUPPORTED = new LockTimeout(MAX_SUPPORTED_TIMEOUT);

    private final long valueInMillis;
    private final String displayValue;

    private LockTimeout(long valueInMillis) {
        this.valueInMillis = valueInMillis;

        if (valueInMillis == SYSTEM_DEFAULT_TIMEOUT) {
            this.displayValue = "LockTimeout(SYSTEM_DEFAULT)";
        } else if (valueInMillis == MAX_SUPPORTED_TIMEOUT) {
            this.displayValue = "LockTimeout(MAX_SUPPORTED)";
        } else {
            this.displayValue = "LockTimeout(" + valueInMillis + ")";
        }
    }

    /**
     * Factory method for creating {@link LockTimeout} objects
     *
     * @param valueInMillis timeout value in milliseconds
     * @return {@link LockTimeout} instance
     * @throws IllegalArgumentException if the argument is not a valid timeout
     */
    public static LockTimeout of(long valueInMillis) {
        if (valueInMillis == SYSTEM_DEFAULT_TIMEOUT) {
            return SYSTEM_DEFAULT;
        } else if (valueInMillis == MAX_SUPPORTED_TIMEOUT) {
            return MAX_SUPPORTED;
        }

        validate(valueInMillis);
        return new LockTimeout(valueInMillis);
    }

    private static void validate(long valueInMillis) {
        if (valueInMillis < 0) {
            throw new IllegalArgumentException("Invalid lock timeout: " + valueInMillis);
        }
    }

    @Override
    public String toString() {
        return displayValue;
    }
}
