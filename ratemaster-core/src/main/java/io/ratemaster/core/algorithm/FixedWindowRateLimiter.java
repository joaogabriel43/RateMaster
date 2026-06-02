package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.FixedWindowConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Core Fixed Window rate limiter that evaluates requests against a distributed
 * bucket stored in Redis.
 *
 * <p>This class manages rate limiting by using a simple counter with an absolute
 * expiration timestamp (Fixed Window). It delegates all state management
 * to an atomic Lua script executed via the {@link LuaScriptExecutor}.</p>
 *
 * @since 0.1.0
 */
public class FixedWindowRateLimiter {

    private static final String KEY_PREFIX = "ratemaster:fixedwindow:";
    private static final String LUA_SCRIPT_PATH = "/lua/fixed_window.lua";

    private final LuaScriptExecutor scriptExecutor;
    private final String luaScript;

    public FixedWindowRateLimiter(LuaScriptExecutor scriptExecutor) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor,
                "scriptExecutor must not be null");
        this.luaScript = loadLuaScript();
    }

    public RateLimitResult tryAcquire(String key, FixedWindowConfig config) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        String redisKey = KEY_PREFIX + key;

        List<String> keys = List.of(redisKey);
        List<String> args = List.of(
                String.valueOf(config.maxCapacity()),
                String.valueOf(config.windowSeconds())
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
        try (InputStream is = FixedWindowRateLimiter.class.getResourceAsStream(LUA_SCRIPT_PATH)) {
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
