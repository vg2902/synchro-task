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
package org.vg2902.synchrotask.jdbc.oracle;

import org.apache.commons.dbcp2.BasicDataSource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.OracleContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Contains Oracle test environment init/shutdown logic.
 * The environment can be configured in two ways:
 * <ul>
 *     <li>
 *         using Docker via <a href=https://www.testcontainers.org/>TestContainers</a> based on
 *         <a href=https://hub.docker.com/r/oracleinanutshell/oracle-xe-11g>Oracle XE 11g</a> image.
 *         Note, that the image size is 2.13Gb, so when you run it for the first time,
 *         it may take a while for Docker to download it.
 *     </li>
 *     <li>using external Oracle database instance;</li>
 * </ul>
 * Check <b>resources/database-setup/oracle/datasource.oracle.properties</b> for the options.
 */
public class OracleResource {

    private static final String datasourcePropertiesFile = "database-setup/oracle/datasource.oracle.properties";
    private static final Properties datasourceProperties = new Properties();

    private static JdbcDatabaseContainer<?> oracle;
    public static DataSource datasource = null;

    public static synchronized void init() throws IOException {
        if (datasource != null)
            return;

        try (InputStreamReader propertiesFile = new InputStreamReader(ClassLoader.getSystemResourceAsStream(datasourcePropertiesFile))) {
            datasourceProperties.load(propertiesFile);
        }

        String userName = datasourceProperties.getProperty("USER_NAME");
        String password = datasourceProperties.getProperty("PASSWORD");
        String driverName = datasourceProperties.getProperty("DRIVER_NAME");

        String url;
        boolean isExternalInstance = !datasourceProperties.getProperty("EXTERNAL_URL", "").trim().isEmpty();

        if (isExternalInstance) {
            url = datasourceProperties.getProperty("EXTERNAL_URL");
        } else {
            String imageName = datasourceProperties.getProperty("DOCKER_IMAGE_NAME");
            String databaseSetupScript = datasourceProperties.getProperty("DATABASE_SETUP_SCRIPT");
            String entryPointPath = datasourceProperties.getProperty("DOCKER_INIT_SQL_SCRIPT_ENTRY_POINT");
            String schemaSetupScript = datasourceProperties.getProperty("SCHEMA_SETUP_SCRIPT");

            oracle = new OracleContainer(imageName)
                    .withClasspathResourceMapping(databaseSetupScript, entryPointPath, BindMode.READ_ONLY)
                    .withUsername(userName)
                    .withPassword(password)
                    .withInitScript(schemaSetupScript);

            oracle.start();
            url = oracle.getJdbcUrl();
        }

        datasource = initDataSource(url, userName, password, driverName);
    }

    private static DataSource initDataSource(String url, String userName, String password, String driverName) {
        BasicDataSource ds = new BasicDataSource();

        ds.setUrl(url);
        ds.setUsername(userName);
        ds.setPassword(password);
        ds.setDriverClassName(driverName);
        ds.setAutoCommitOnReturn(false);
        ds.setDefaultAutoCommit(false);
        ds.setMaxTotal(10);

        return ds;
    }

    public static void shutdown() {
        if (oracle != null && oracle.isRunning())
            oracle.stop();
    }
}
