package org.vg2902.synchrotask.jdbc.h2;

import org.junit.BeforeClass;
import org.junit.Test;
import org.vg2902.synchrotask.jdbc.AbstractSQLRunnerIT;
import org.vg2902.synchrotask.jdbc.SQLRunner;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AbstractSQLRunnerIT} implementation for H2. During build, This test class is meant to be executed
 * as part of {@link H2Test} suite.
 */
public class H2SQLRunnerIT extends AbstractSQLRunnerIT implements H2DatabaseIT {

    @BeforeClass
    public static void init() throws IOException, SQLException {
        H2Resource.init();
    }

    @Override
    public Set<Integer> getDuplicateKeyErrorCodes() {
        return singleton(23505);
    }

    @Override
    public Set<Integer> getCannotAcquireLockErrorCodes() {
        return singleton(50200);
    }

    /*
     * H2-specific tests
     */

    @Test
    public void lockTimeoutIsZeroForNonWaitingTasks() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final SQLRunner sqlRunner1 = new SQLRunner(dataSource, TABLE_NAME, getThrowingTestSynchroTask("TaskName1", "TaskId1"));
             final SQLRunner sqlRunner2 = new SQLRunner(dataSource, TABLE_NAME, getReturningTestSynchroTask("TaskName2", "TaskId2"))) {

            assertThat(getLockTimeout(sqlRunner1.getConnection())).isZero();
            assertThat(getLockTimeout(sqlRunner2.getConnection())).isZero();
        }
    }

    @Test
    public void lockTimeoutRemainsUnchangedForWaitingTasks() throws SQLException {
        DataSource dataSource = getDataSource();

        try (final SQLRunner sqlRunner = new SQLRunner(dataSource, TABLE_NAME, getWaitingTestSynchroTask("TaskName1", "TaskId1"))) {
            assertThat(getLockTimeout(sqlRunner.getConnection())).isEqualTo(H2Resource.DEFAULT_LOCK_TIMEOUT_IN_MILLIS);
        }
    }

    private Long getLockTimeout(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("CALL LOCK_TIMEOUT()");
            rs.next();
            return rs.getLong(1);
        }
    }
}
