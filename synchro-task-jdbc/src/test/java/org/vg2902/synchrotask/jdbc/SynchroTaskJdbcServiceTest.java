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
package org.vg2902.synchrotask.jdbc;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.jdbc.SynchroTaskJdbcService.SynchroTaskJdbcServiceBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(MockitoJUnitRunner.class)
public class SynchroTaskJdbcServiceTest {

    @Mock
    DataSource ds;

    @Test
    public void buildsSynchroTaskJdbcServiceWithDefaultSettings() {
        SynchroTaskJdbcService service = SynchroTaskJdbcService
                .from(ds)
                .build();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.getDataSource()).isEqualTo(ds);
        assertions.assertThat(service.getTableName()).isEqualTo("SYNCHRO_TASK");
        assertions.assertThat(service.getInterceptor()).isEqualTo(null);
        assertions.assertAll();
    }

    @Test
    public void buildsSynchroTaskJdbcServiceWithTableName() {
        SynchroTaskJdbcService service = SynchroTaskJdbcService
                .from(ds)
                .withTableName("CUSTOM_SYNCHRO_TASK")
                .build();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.getDataSource()).isEqualTo(ds);
        assertions.assertThat(service.getTableName()).isEqualTo("CUSTOM_SYNCHRO_TASK");
        assertions.assertThat(service.getInterceptor()).isEqualTo(null);
        assertions.assertAll();
    }

    @Test
    public void buildsSynchroTaskJdbcServiceWithInterceptor() {
        BiConsumer<SynchroTask<?>, Connection> noopInterceptor = (task, connection) -> {
        };

        SynchroTaskJdbcService service = SynchroTaskJdbcService
                .from(ds)
                .withInterceptor(noopInterceptor)
                .build();

        SoftAssertions assertions = new SoftAssertions();
        assertions.assertThat(service.getDataSource()).isEqualTo(ds);
        assertions.assertThat(service.getTableName()).isEqualTo("SYNCHRO_TASK");
        assertions.assertThat(service.getInterceptor()).isEqualTo(noopInterceptor);
        assertions.assertAll();
    }

    @Test
    public void doesNotAcceptNullDatasource() {
        assertThatThrownBy(() -> SynchroTaskJdbcService.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullTableName() {
        SynchroTaskJdbcServiceBuilder builder = SynchroTaskJdbcService.from(ds);
        assertThatThrownBy(() -> builder.withTableName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void doesNotAcceptNullInterceptor() {
        SynchroTaskJdbcServiceBuilder builder = SynchroTaskJdbcService.from(ds);
        assertThatThrownBy(() -> builder.withInterceptor(null)).isInstanceOf(NullPointerException.class);
    }
}