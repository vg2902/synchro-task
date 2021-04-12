package org.vg2902.synchrotask.jdbc.h2;

import org.junit.BeforeClass;
import org.vg2902.synchrotask.jdbc.AbstractSynchroTaskJdbcServiceIT;

import java.io.IOException;
import java.sql.SQLException;

/**
 * {@link AbstractSynchroTaskJdbcServiceIT} implementation for H2. During build, this test class is meant
 * to be executed as part of {@link H2Test} suite.
 */
public class H2SynchroTaskJdbcServiceIT extends AbstractSynchroTaskJdbcServiceIT implements H2DatabaseIT {

    @BeforeClass
    public static void init() throws IOException, SQLException {
        H2Resource.init();
    }
}
