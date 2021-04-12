package org.vg2902.synchrotask.jdbc.h2;

import org.vg2902.synchrotask.jdbc.DatabaseIT;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * H2-specific version of {@link DatabaseIT}.
 */
public interface H2DatabaseIT extends DatabaseIT {

    @Override
    default DataSource getDataSource() {
        return H2Resource.datasource;
    }

    @Override
    default Long getSessionId(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("CALL SESSION_ID()");
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default boolean isDatabaseSessionBlocked(Long blockedSessionId, Long blockingSessionId) {
        DataSource dataSource = getDataSource();

        System.out.println("Blocked session id :"+blockedSessionId);
        System.out.println("Blocking session id :"+blockingSessionId);

        try (final Connection connection = dataSource.getConnection();
             final PreparedStatement statement = connection.prepareStatement("SELECT * FROM information_schema.sessions WHERE id = ? AND blocker_id = ?")) {

            statement.setLong(1, blockedSessionId);
            statement.setLong(2, blockingSessionId);

            ResultSet rs = statement.executeQuery();
            return rs.next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
