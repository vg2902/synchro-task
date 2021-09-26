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

import org.apache.commons.dbcp2.BasicDataSource;
import org.h2.Driver;
import org.h2.tools.RunScript;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Contains H2 test environment init/shutdown logic.
 * The environment is based on an in-memory H2 database instance which is created during tests run.
 * <p>
 * Check <b>resources/database-setup/h2/datasource.h2.properties</b> for the options.
 */
public class H2Resource {

    private static final String datasourcePropertiesFile = "database-setup/h2/datasource.h2.properties";
    private static final Properties datasourceProperties = new Properties();

    public static int DEFAULT_LOCK_TIMEOUT_IN_MILLIS;

    public static DataSource datasource = null;

    public static synchronized void init() throws SQLException, IOException {
        if (datasource != null)
            return;

        try (InputStreamReader propertiesFile = new InputStreamReader(ClassLoader.getSystemResourceAsStream(datasourcePropertiesFile))) {
            datasourceProperties.load(propertiesFile);
        }

        String url = datasourceProperties.getProperty("URL");
        String userName = datasourceProperties.getProperty("USER_NAME");
        String password = datasourceProperties.getProperty("PASSWORD");
        String schemaSetupScript = datasourceProperties.getProperty("SCHEMA_SETUP_SCRIPT");

        String defaultLockTimeoutInMillis = datasourceProperties.getProperty("DEFAULT_LOCK_TIMEOUT_IN_MILLIS");
        String effectiveUrl = url + ";LOCK_TIMEOUT=" + defaultLockTimeoutInMillis;
        DEFAULT_LOCK_TIMEOUT_IN_MILLIS = Integer.parseInt(defaultLockTimeoutInMillis);

        datasource = initDataSource(effectiveUrl, userName, password, schemaSetupScript);
    }

    private static DataSource initDataSource(String url, String userName, String password, String schemaSetupScriptLocation) throws SQLException, IOException {
        BasicDataSource ds = new BasicDataSource();

        ds.setUrl(url);
        ds.setUsername(userName);
        ds.setPassword(password);
        ds.setDriver(Driver.load());
        ds.setAutoCommitOnReturn(false);
        ds.setDefaultAutoCommit(false);
        ds.setMaxTotal(10);

        try (InputStreamReader initScript = new InputStreamReader(ClassLoader.getSystemResourceAsStream(schemaSetupScriptLocation));
             Connection connection = ds.getConnection()) {
            RunScript.execute(connection, initScript);
        }

        return ds;
    }
}
