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

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.vg2902.synchrotask.core.api.LockTimeout.MAX_SUPPORTED;
import static org.vg2902.synchrotask.core.api.LockTimeout.MAX_SUPPORTED_TIMEOUT;
import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT;
import static org.vg2902.synchrotask.core.api.LockTimeout.SYSTEM_DEFAULT_TIMEOUT;

public class LockTimeoutTest {

    @Test
    public void createsSystemDefaultLockTimeout() {
        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(SYSTEM_DEFAULT.getValueInMillis()).isEqualTo(SYSTEM_DEFAULT_TIMEOUT);
        assertions.assertThat(LockTimeout.of(SYSTEM_DEFAULT_TIMEOUT)).isSameAs(SYSTEM_DEFAULT);
        assertions.assertAll();
    }

    @Test
    public void createsMaxSupportedLockTimeout() {
        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(MAX_SUPPORTED.getValueInMillis()).isEqualTo(MAX_SUPPORTED_TIMEOUT);
        assertions.assertThat(LockTimeout.of(MAX_SUPPORTED_TIMEOUT)).isSameAs(MAX_SUPPORTED);
        assertions.assertAll();
    }

    @Test
    public void createsCustomLockTimeout() {
        assertThat(LockTimeout.of(10L).getValueInMillis()).isEqualTo(10L);
    }

    @Test
    public void validatesCustomTimeout() {
        assertThatThrownBy(() -> LockTimeout.of(-3L)).isInstanceOf(IllegalArgumentException.class);
    }
}