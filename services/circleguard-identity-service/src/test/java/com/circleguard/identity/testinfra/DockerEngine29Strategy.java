package com.circleguard.identity.testinfra;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.RemoteApiVersion;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.InvalidConfigurationException;

/**
 * Custom Testcontainers DockerClientProviderStrategy that satisfies Docker
 * Desktop 4.61+ / Engine 29.x, which enforce a minimum API version of 1.44.
 *
 * Root cause of the failure in CI:
 *   All built-in Testcontainers 1.19.x strategies call
 *   {@code DefaultDockerClientConfig.createDefaultConfigBuilder()}, which
 *   creates a bare builder with the hardcoded default of API 1.41.
 *   {@code createDefaultConfigBuilder()} never reads {@code DOCKER_API_VERSION}
 *   from the environment — only {@code fromEnv()} does. Therefore, every
 *   request docker-java sends uses {@code /v1.41/...} in the URL, and Docker
 *   Desktop 4.61 / Engine 29.x rejects it with:
 *     "client version 1.41 is too old. Minimum supported API version is 1.44."
 *
 * Fix:
 *   This strategy explicitly calls {@code .withApiVersion(RemoteApiVersion.parseConfig("1.44"))}
 *   on the builder, guaranteeing {@code /v1.44/...} is used regardless of the
 *   environment-variable configuration. It is referenced in
 *   {@code src/test/resources/testcontainers.properties} so that Testcontainers
 *   loads it instead of auto-detecting a built-in strategy.
 */
public class DockerEngine29Strategy extends DockerClientProviderStrategy {

    private static final String API_VERSION = "1.44";

    @Override
    public DockerClientConfig getConfig() {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank()) {
            dockerHost = "unix:///var/run/docker.sock";
        }
        return DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withApiVersion(RemoteApiVersion.parseConfig(API_VERSION))
                .build();
    }

    @Override
    protected boolean isApplicable() {
        return new java.io.File("/var/run/docker.sock").exists()
                || System.getenv("DOCKER_HOST") != null;
    }

    @Override
    public String getDescription() {
        return "Docker Engine 29.x / Docker Desktop 4.61+ compatible strategy "
                + "(Unix socket, API " + API_VERSION + ")";
    }
}
