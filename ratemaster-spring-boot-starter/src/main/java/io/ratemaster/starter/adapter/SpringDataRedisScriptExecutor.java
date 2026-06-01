package io.ratemaster.starter.adapter;

import io.ratemaster.core.port.LuaScriptExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link LuaScriptExecutor} using Spring Data Redis.
 *
 * <p>This class bridges the core rate limiting logic with the Spring Data ecosystem.
 * It uses {@link StringRedisTemplate} to execute the pre-loaded Lua script atomically.
 * By relying on Spring Data, this adapter is fully client-agnostic and will work
 * seamlessly whether the underlying driver is Lettuce or Jedis.</p>
 *
 * @since 0.1.0
 */
public class SpringDataRedisScriptExecutor implements LuaScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    /**
     * Creates a new instance of the adapter.
     *
     * @param redisTemplate the StringRedisTemplate provided by Spring Boot auto-configuration;
     *                      must not be {@code null}
     * @param script         a singleton {@link RedisScript} configured to return a {@link List};
     *                      must not be {@code null}
     */
    public SpringDataRedisScriptExecutor(StringRedisTemplate redisTemplate, RedisScript<List> script) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.script = Objects.requireNonNull(script, "script must not be null");
    }

    /**
     * Executes the Lua script via Spring Data Redis.
     *
     * <p>The raw result from Spring Data Redis is a {@code List<Object>} where elements
     * could be {@code String} or {@code Long} depending on the underlying driver and
     * Redis protocol. This method safely maps all elements to {@code Long} as expected
     * by the core rate limiter.</p>
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object eval(String scriptSource, List<String> keys, List<String> args) {
        // We ignore the scriptSource from the core because we use the pre-configured
        // RedisScript bean which caches the SHA1 for performance (EVALSHA).
        
        List<Object> rawResult = redisTemplate.execute(script, keys, args.toArray(new Object[0]));
        
        if (rawResult == null) {
            throw new IllegalStateException("Redis script execution returned null");
        }

        // Safely map driver-specific return types (String or Long) to Long
        return rawResult.stream()
                .map(this::toLong)
                .collect(Collectors.toList());
    }

    private Long toLong(Object value) {
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
                "Cannot convert Redis result element to Long: " + value + 
                " (type: " + (value != null ? value.getClass() : "null") + ")");
    }
}
