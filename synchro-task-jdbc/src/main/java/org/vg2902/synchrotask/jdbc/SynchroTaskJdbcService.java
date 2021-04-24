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
package org.vg2902.synchrotask.jdbc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.vg2902.synchrotask.core.api.CollisionStrategy;
import org.vg2902.synchrotask.core.api.SynchroTask;
import org.vg2902.synchrotask.core.api.SynchroTaskService;
import org.vg2902.synchrotask.core.exception.SynchroTaskCollisionException;
import org.vg2902.synchrotask.core.exception.SynchroTaskException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

/**
 * {@link SynchroTaskService} implementation which uses a special "registry" table in a database to keep track
 * of running tasks and ensure synchronization.
 * The SQL below gives an example of such a table:
 * <pre>
 *  CREATE TABLE synchro_task(
 *        task_name         VARCHAR2(100 CHAR) NOT NULL
 *      , task_id           VARCHAR2(100 CHAR) NOT NULL
 *      , creation_time     TIMESTAMP(9)
 *      , CONSTRAINT synchro_task_pk PRIMARY KEY (task_name, task_id));
 * </pre>
 * The table <b>must</b> have:
 * <ul>
 *  <li>all three columns with exactly the same names and data types as they are defined above;</li>
 *  <li>a composite primary key based on <b>taskName</b> and <b>taskId</b>;</li>
 * </ul>
 * Column size, however, is not limited and can vary. Just make sure that <b>taskName</b> and <b>taskId</b> columns
 * are wide enough to fit anticipated values.
 * <p>
 * By default, the service expects the table to be named <b>SYNCHRO_TASK</b>, but it can be overridden
 * during initialization, see the builder use cases below.
 * <p>
 * In order to construct a service instance, use a builder method {@link #from(DataSource)}:
 * <pre>
 *     DataSource ds = getDataSource();
 *
 *     SynchroTaskService service = SynchroTaskJdbcService
 *          .from(ds)
 *          .build;
 * </pre>
 *
 * There are also two optional parameters supported by the builder:
 *
 * <ul>
 *  <li>registry table name - defaults to <b>SYNCHRO_TASK</b> if not provided;</li>
 *  <li>interceptor - gets triggered as part of {@link #run(SynchroTask)} invocations, capturing the task passed in
 *  and the {@link Connection} allocated for it. Mostly for testing and debugging purposes. </li>
 * </ul>
 *
 * This snippet shows a possible usage of the builder with optional arguments:
 *
 * <pre>
 *     DataSource ds = getDataSource();
 *
 *     SynchroTaskService service = SynchroTaskJdbcService
 *          .from(getDataSource())
 *          .withTableName("CUSTOM_SYNCHRO_TASK")
 *          .withInterceptor((task, connection) -&gt; this::intercept))
 *          .build();
 * </pre>
 *
 * where <b>intercept</b> method can be defined as
 *
 * <pre>
 *     private void intercept(SynchroTask&lt;?&gt; task, Connection connection) {
 *         // do something
 *     }
 * </pre>
 *
 * @see SynchroTask
 * @see SynchroTaskService
 * @see CollisionStrategy
 */
@Getter
@Slf4j
public class SynchroTaskJdbcService implements SynchroTaskService {

    public static final String DEFAULT_TABLE = "SYNCHRO_TASK";

    private final DataSource dataSource;
    private final String tableName;
    private final BiConsumer<SynchroTask<?>, Connection> interceptor;

    public SynchroTaskJdbcService(DataSource dataSource, String tableName, BiConsumer<SynchroTask<?>, Connection> interceptor) {
        this.dataSource = requireNonNull(dataSource);
        this.tableName = requireNonNull(tableName);
        this.interceptor = interceptor;
    }

    public static SynchroTaskJdbcServiceBuilder from(DataSource dataSource) {
        return SynchroTaskJdbcServiceBuilder.from(dataSource);
    }

    /**
     * Executes the given <b>task</b> with respect to its {@link CollisionStrategy} using
     * the registry table to keep track of running tasks and ensure synchronization.
     * <br>
     * Prior to the <b>task</b> executing, it will try to create and immediately lock a "control" row
     * in the registry table with the given task <b>taskName</b> and <b>taskId</b>.
     * <p>
     * If the row already exists and is unlocked, then the service will try to reuse it.
     * <p>
     * If the row already exists and is locked by another database session, then the {@link SynchroTask}
     * will be assumed as being currently executed, and the operation outcome will depend
     * on the task {@link CollisionStrategy}.
     * <p>
     * Every invocation obtains a new {@link Connection} instance from the {@link DataSource}
     * provided during initialization, to manage a task control row in the registry table. Connections are closed
     * when the method returns.
     * <p>
     * After successful {@link SynchroTask} completion, the row will be removed from the table, and the <b>task</b>
     * result will be returned.
     *
     * @param task {@link SynchroTask} instance
     * @param <T> <b>task</b> return type
     * @throws SynchroTaskCollisionException when <b>task</b> has {@link CollisionStrategy#THROW},
     * and another {@link SynchroTask} instance with the same <b>taskName</b> and <b>taskId</b> is still running
     * @throws SynchroTaskException in case of any unhandled exception occurred during {@link SynchroTask} execution.
     * @throws UnsupportedDatabaseException if the database is not supported
     * @return <b>task</b> return value
     */
    @Override
    public <T> T run(SynchroTask<T> task) {
        T result;

        try (SQLRunner sqlRunner = new SQLRunner(dataSource, tableName, task);
             LockManager lockManager = new LockManager(sqlRunner)) {

            if (interceptor != null)
                interceptor.accept(task, sqlRunner.getConnection());

            if (!lockManager.lock()) {
                log.debug("Cannot get lock for {}", task);

                switch (task.getCollisionStrategy()) {
                    case RETURN:
                        return null;
                    case WAIT:
                    case THROW:
                        throw new SynchroTaskCollisionException(task);
                    default:
                        throw new IllegalStateException("Unexpected value: " + task.getCollisionStrategy());
                }
            }

            result = task.execute();
            lockManager.unlock();
        } catch (SynchroTaskException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception occurred while executing {}", task);
            throw new SynchroTaskException(e);
        }

        return result;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SynchroTaskJdbcServiceBuilder {

        private DataSource dataSource;
        private String tableName = DEFAULT_TABLE;
        private BiConsumer<SynchroTask<?>, Connection> interceptor;

        public static SynchroTaskJdbcServiceBuilder from(DataSource dataSource) {
            SynchroTaskJdbcServiceBuilder builder = new SynchroTaskJdbcServiceBuilder();
            builder.dataSource = requireNonNull(dataSource);
            return builder;
        }

        public SynchroTaskJdbcServiceBuilder withTableName(String tableName) {
            this.tableName = requireNonNull(tableName);
            return this;
        }

        public SynchroTaskJdbcServiceBuilder withInterceptor(BiConsumer<SynchroTask<?>, Connection> interceptor) {
            this.interceptor = requireNonNull(interceptor);
            return this;
        }

        public SynchroTaskJdbcService build() {
            return new SynchroTaskJdbcService(dataSource, tableName, interceptor);
        }
    }
}
