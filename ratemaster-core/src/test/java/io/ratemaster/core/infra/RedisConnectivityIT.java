package io.ratemaster.core.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Infrastructure smoke test that verifies Redis connectivity via Testcontainers.
 *
 * <p>This integration test spins up a Redis container, establishes a connection
 * using the Jedis client, sends a {@code PING} command and asserts the expected
 * {@code PONG} response. It serves as the foundational infrastructure validation
 * for the RateMaster project.</p>
 *
 * <p>Named with the {@code IT} suffix to be picked up by the Maven Failsafe plugin
 * during the {@code integration-test} phase.</p>
 *
 * @since 0.1.0
 */
@Testcontainers
@DisplayName("Redis Connectivity Smoke Test")
class RedisConnectivityIT {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    /**
     * Verifies that a Redis container started via Testcontainers responds
     * to the {@code PING} command with {@code PONG}.
     */
    @Test
    @DisplayName("should receive PONG response from Redis PING command")
    void shouldRespondWithPongWhenPingSent() {
        String host = REDIS.getHost();
        Integer mappedPort = REDIS.getMappedPort(REDIS_PORT);

        assertNotNull(host, "Redis container host should not be null");
        assertNotNull(mappedPort, "Redis container mapped port should not be null");

        try (Jedis jedis = new Jedis(host, mappedPort)) {
            String response = jedis.ping();
            assertEquals("PONG", response,
                    "Redis should respond with PONG to a PING command");
        }
    }

    /**
     * Verifies that the Redis container is running and accessible.
     */
    @Test
    @DisplayName("should confirm Redis container is running")
    void shouldConfirmContainerIsRunning() {
        assertEquals(true, REDIS.isRunning(),
                "Redis container should be in running state");
    }
}
