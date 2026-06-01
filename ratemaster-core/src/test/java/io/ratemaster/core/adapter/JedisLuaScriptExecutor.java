package io.ratemaster.core.adapter;

import io.ratemaster.core.port.LuaScriptExecutor;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Objects;

/**
 * Test-only adapter implementing {@link LuaScriptExecutor} using the Jedis Redis client.
 *
 * <p>This adapter exists exclusively in {@code src/test/java} and is used for
 * integration testing the core module against a real Redis instance (typically
 * provisioned via Testcontainers). It bridges the hexagonal port to the Jedis
 * client without introducing any production dependency on Jedis.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This adapter creates a new {@link Jedis} connection for each {@code eval} call,
 * making it safe for concurrent use from multiple test threads. Each call is
 * independent and does not share connection state.</p>
 *
 * @since 0.1.0
 * @see LuaScriptExecutor
 */
public class JedisLuaScriptExecutor implements LuaScriptExecutor {

    private final String host;
    private final int port;

    /**
     * Creates a new adapter targeting the given Redis host and port.
     *
     * @param host the Redis server hostname; must not be {@code null}
     * @param port the Redis server port; must be a valid port number
     */
    public JedisLuaScriptExecutor(String host, int port) {
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.port = port;
    }

    /**
     * Executes the given Lua script against Redis using a fresh Jedis connection.
     *
     * <p>The connection is opened and closed for each invocation. This approach
     * prioritizes simplicity and thread safety over connection pooling, which is
     * acceptable for test workloads.</p>
     *
     * {@inheritDoc}
     */
    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
        try (Jedis jedis = new Jedis(host, port)) {
            return jedis.eval(script, keys, args);
        }
    }
}
