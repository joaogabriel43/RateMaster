package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Core Token Bucket rate limiter that evaluates requests against a distributed
 * bucket stored in Redis.
 *
 * <p>This class is the primary entry point for the Token Bucket algorithm. It
 * delegates all state management to an atomic Lua script executed via the
 * {@link LuaScriptExecutor} hexagonal port, ensuring thread-safety and
 * multi-instance consistency without any application-level locking.</p>
 *
 * <h3>Architecture (ADR-005)</h3>
 * <p>This class has zero dependencies on Spring Framework or any specific Redis
 * client. The {@code LuaScriptExecutor} port is injected via constructor,
 * allowing different adapters for production (Spring Data Redis) and testing
 * (Jedis + Testcontainers).</p>
 *
 * <h3>Key Format</h3>
 * <p>Redis keys follow the pattern: {@code ratemaster:tokenbucket:{key}}</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. All mutable state resides in Redis and is
 * managed atomically by the Lua script.</p>
 *
 * @since 0.1.0
 */
public class TokenBucketRateLimiter {

    private static final String KEY_PREFIX = "ratemaster:tokenbucket:";
    private static final String LUA_SCRIPT_PATH = "/lua/token_bucket.lua";
    private static final int DEFAULT_COST = 1;

    private final LuaScriptExecutor scriptExecutor;
    private final String luaScript;

    /**
     * Creates a new {@code TokenBucketRateLimiter} backed by the given script executor.
     *
     * @param scriptExecutor the hexagonal port for executing Lua scripts; must not be {@code null}
     * @throws IllegalStateException if the Lua script resource cannot be loaded from the classpath
     */
    public TokenBucketRateLimiter(LuaScriptExecutor scriptExecutor) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor,
                "scriptExecutor must not be null");
        this.luaScript = loadLuaScript();
    }

    /**
     * Attempts to acquire a single token from the bucket identified by the given key.
     *
     * <p>The key is automatically prefixed with {@code ratemaster:tokenbucket:} to
     * form the full Redis key. The evaluation is performed atomically on the Redis
     * server via a Lua script, ensuring no race conditions under concurrent access.</p>
     *
     * @param key    the logical identifier for this rate limit bucket (e.g.,
     *               {@code "api:/login:192.168.1.1"}); must not be {@code null} or blank
     * @param config the token bucket configuration defining capacity and refill rate;
     *               must not be {@code null}
     * @return a {@link RateLimitResult} indicating whether the request was allowed,
     *         the remaining tokens, and the retry-after delay if rejected
     * @throws IllegalArgumentException if {@code key} is {@code null} or blank
     */
    public RateLimitResult tryAcquire(String key, TokenBucketConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        String redisKey = KEY_PREFIX + key;

        List<String> keys = List.of(redisKey);
        List<String> args = List.of(
                String.valueOf(config.maxCapacity()),
                String.valueOf(config.refillRatePerSecond()),
                String.valueOf(DEFAULT_COST)
        );

        Object result = scriptExecutor.eval(luaScript, keys, args);
        return parseResult(result);
    }

    /**
     * Parses the raw Lua script result into a typed {@link RateLimitResult}.
     *
     * <p>The Lua script returns a list of three elements:
     * {@code [allowed (0|1), remainingTokens, retryAfterMillis]}.</p>
     *
     * @param result the raw result from Lua script execution
     * @return the parsed {@code RateLimitResult}
     */
    @SuppressWarnings("unchecked")
    private RateLimitResult parseResult(Object result) {
        if (!(result instanceof List<?> list) || list.size() < 3) {
            throw new IllegalStateException(
                    "Unexpected Lua script result format: " + result);
        }

        long allowed = toLong(list.get(0));
        long remaining = toLong(list.get(1));
        long retryAfterMillis = toLong(list.get(2));

        if (allowed == 1) {
            return RateLimitResult.allowed(remaining);
        } else {
            return RateLimitResult.rejected(remaining, retryAfterMillis);
        }
    }

    /**
     * Converts a Lua return value to a Java {@code long}.
     *
     * @param value the value to convert (typically {@code Long} or {@code String})
     * @return the long value
     */
    private long toLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            return Long.parseLong(s);
        }
        throw new IllegalStateException(
                "Cannot convert Lua result to long: " + value + " (type: " + value.getClass() + ")");
    }

    /**
     * Loads the Token Bucket Lua script from the classpath.
     *
     * @return the Lua script source code as a string
     * @throws IllegalStateException if the script resource is not found or cannot be read
     */
    private static String loadLuaScript() {
        try (InputStream is = TokenBucketRateLimiter.class.getResourceAsStream(LUA_SCRIPT_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Lua script not found on classpath: " + LUA_SCRIPT_PATH);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load Lua script from: " + LUA_SCRIPT_PATH, e);
        }
    }
}
