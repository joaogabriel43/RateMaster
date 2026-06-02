package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.SlidingWindowConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Core Sliding Window rate limiter that evaluates requests against a distributed
 * bucket stored in Redis.
 *
 * <p>This class manages rate limiting by counting events in a continuous sliding
 * time window using Redis Sorted Sets (ZSET). It delegates all state management
 * to an atomic Lua script executed via the {@link LuaScriptExecutor}.</p>
 *
 * @since 0.1.0
 */
public class SlidingWindowRateLimiter {

    private static final String KEY_PREFIX = "ratemaster:slidingwindow:";
    private static final String LUA_SCRIPT_PATH = "/lua/sliding_window.lua";

    private final LuaScriptExecutor scriptExecutor;
    private final String luaScript;

    public SlidingWindowRateLimiter(LuaScriptExecutor scriptExecutor) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor,
                "scriptExecutor must not be null");
        this.luaScript = loadLuaScript();
    }

    public RateLimitResult tryAcquire(String key, SlidingWindowConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        String redisKey = KEY_PREFIX + key;
        String uniqueId = UUID.randomUUID().toString();

        List<String> keys = List.of(redisKey);
        List<String> args = List.of(
                String.valueOf(config.maxCapacity()),
                String.valueOf(config.windowSeconds()),
                uniqueId
        );

        Object result = scriptExecutor.eval(luaScript, keys, args);
        return parseResult(result);
    }

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
                "Cannot convert Lua result to long: " + value + " (type: " + (value != null ? value.getClass() : "null") + ")");
    }

    private static String loadLuaScript() {
        try (InputStream is = SlidingWindowRateLimiter.class.getResourceAsStream(LUA_SCRIPT_PATH)) {
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
