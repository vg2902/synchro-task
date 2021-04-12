package org.vg2902.synchrotask.jdbc.h2;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Integration test suite for H2.
 */
@RunWith(Suite.class)
@SuiteClasses({H2SQLRunnerIT.class, H2SynchroTaskJdbcServiceIT.class})
public class H2Test {
}
