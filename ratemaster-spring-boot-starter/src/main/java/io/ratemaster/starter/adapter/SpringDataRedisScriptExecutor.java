package io.ratemaster.starter.adapter;

import io.ratemaster.core.port.LuaScriptExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link LuaScriptExecutor} using Spring Data Redis.
 *
 * <p>This class bridges the core rate limiting logic with the Spring Data ecosystem.
 * It uses {@link StringRedisTemplate} to execute the Lua script atomically.
 * It dynamically caches scripts to maintain high performance with EVALSHA.</p>
 *
 * @since 0.1.0
 */
public class SpringDataRedisScriptExecutor implements LuaScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final ConcurrentMap<String, RedisScript<List>> scriptCache = new ConcurrentHashMap<>();

    /**
     * Creates a new instance of the adapter.
     *
     * @param redisTemplate the StringRedisTemplate provided by Spring Boot auto-configuration;
     *                      must not be {@code null}
     */
    public SpringDataRedisScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /**
     * Executes the Lua script via Spring Data Redis.
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object eval(String scriptSource, List<String> keys, List<String> args) {
        
        RedisScript<List> script = scriptCache.computeIfAbsent(scriptSource, source -> {
            DefaultRedisScript<List> s = new DefaultRedisScript<>();
            s.setScriptText(source);
            s.setResultType(List.class);
            return s;
        });
        
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
