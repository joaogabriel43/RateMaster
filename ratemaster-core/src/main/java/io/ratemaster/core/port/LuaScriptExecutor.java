package io.ratemaster.core.port;

import java.util.List;

/**
 * Hexagonal port for executing Lua scripts against a Redis-compatible backend.
 *
 * <p>This interface decouples the core rate limiting algorithms from any specific
 * Redis client implementation (Jedis, Lettuce, Spring Data Redis, etc.), enabling
 * the {@code ratemaster-core} module to remain entirely free of infrastructure
 * dependencies.</p>
 *
 * <p><b>Contract:</b> Implementations must guarantee that the Lua script is executed
 * atomically on the Redis server, as per Redis' EVAL/EVALSHA semantics. The script
 * execution must be blocking from the caller's perspective and must propagate any
 * connection or execution errors as runtime exceptions.</p>
 *
 * <h3>Adapter Strategy (ADR-005)</h3>
 * <ul>
 *   <li><b>Production:</b> The {@code ratemaster-spring-boot-starter} module provides
 *       an adapter backed by Spring Data Redis' {@code RedisConnectionFactory}.</li>
 *   <li><b>Testing:</b> A Jedis-based adapter in {@code src/test/java} connects
 *       directly to a Testcontainers Redis instance.</li>
 * </ul>
 *
 * @since 0.1.0
 * @see <a href="https://redis.io/commands/eval">Redis EVAL command</a>
 */
public interface LuaScriptExecutor {

    /**
     * Executes a Lua script on the Redis server with the given keys and arguments.
     *
     * <p>The script is executed atomically. The {@code keys} list maps to the
     * {@code KEYS} global table in Lua (1-based), and the {@code args} list maps
     * to the {@code ARGV} global table.</p>
     *
     * @param script the Lua script source code to execute; must not be {@code null}
     * @param keys   the list of Redis keys accessed by the script; must not be {@code null}
     * @param args   the list of additional arguments for the script; must not be {@code null}
     * @return the result of the Lua script execution, whose type depends on the script's
     *         return value (typically a {@code List}, {@code Long}, or {@code String})
     * @throws RuntimeException if the script execution fails due to connection issues,
     *                          syntax errors, or Redis server errors
     */
    Object eval(String script, List<String> keys, List<String> args);
}
