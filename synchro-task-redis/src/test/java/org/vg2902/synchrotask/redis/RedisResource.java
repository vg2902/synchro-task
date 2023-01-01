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
package org.vg2902.synchrotask.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Contains Redis test environment init/shutdown logic.
 * The environment can be configured in two ways:
 * <ul>
 *     <li>
 *         using Docker via <a href=https://www.testcontainers.org/>TestContainers</a> based on
 *         <a href=https://hub.docker.com/_/redis>Redis</a> image
 *     </li>
 *     <li>using external Redis instance;</li>
 * </ul>
 * Check <b>resources/redis.properties</b> for the options.
 */
public class RedisResource {

    private static final String redisPropertiesFile = "redis.properties";
    private static final Properties redisProperties = new Properties();

    private static final int redisPort = 6379;

    private static GenericContainer<?> redis;

    public static RedissonClient redissonClient = null;
    public static RedisConnection redisConnection = null;

    public static synchronized void init() throws IOException {
        if (redissonClient != null)
            return;

        try (InputStreamReader propertiesFile = new InputStreamReader(ClassLoader.getSystemResourceAsStream(redisPropertiesFile))) {
            redisProperties.load(propertiesFile);
        }

        String url;
        boolean isExternalInstance = !redisProperties.getProperty("EXTERNAL_URL", "").trim().isEmpty();

        if (isExternalInstance) {
            url = redisProperties.getProperty("EXTERNAL_URL");
        } else {
            String imageName = redisProperties.getProperty("DOCKER_IMAGE_NAME");

            redis = new GenericContainer<>(DockerImageName.parse(imageName))
                    .withExposedPorts(redisPort);

            redis.start();
            url = buildRedisUrl(redis.getHost(), redis.getFirstMappedPort());
        }

        initRedisson(url);
    }

    private static String buildRedisUrl(String host, int port) {
        return String.format("redis://%1$S:%2$d", host, port);
    }

    private static void initRedisson(String url) {
        initRedissonClient(url);
        initNativeRedisConnection(url);
    }

    private static void initNativeRedisConnection(String url) {
        RedisClientConfig clientConfig = new RedisClientConfig();
        clientConfig.setAddress(url);
        RedisClient client = RedisClient.create(clientConfig);

        redisConnection = client.connect();
    }

    private static void initRedissonClient(String url) {
        Config config = new Config();
        config.useSingleServer().setAddress(url);
        config.setLockWatchdogTimeout(30000);

        redissonClient = Redisson.create(config);
    }

    public static void shutdown() {
        if (redis != null && redis.isRunning())
            redis.stop();
    }
}
