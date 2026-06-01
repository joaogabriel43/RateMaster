package io.ratemaster.core.algorithm;

import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link TokenBucketRateLimiter} against a real Redis instance.
 *
 * <p>These tests verify the functional correctness of the Token Bucket algorithm
 * including initialization, token consumption, exhaustion, lazy refill, and
 * retry-after calculation. All tests run against a Testcontainers Redis instance.</p>
 *
 * @since 0.1.0
 */
@Testcontainers
@DisplayName("Token Bucket Rate Limiter - Functional Integration Tests")
class TokenBucketRateLimiterIT {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        JedisLuaScriptExecutor executor = new JedisLuaScriptExecutor(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        rateLimiter = new TokenBucketRateLimiter(executor);
    }

    /**
     * Generates a unique key for each test to avoid cross-test contamination.
     */
    private String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("should allow request when bucket has tokens")
    void shouldAllowRequestWhenBucketHasTokens() {
        TokenBucketConfig config = new TokenBucketConfig(10, 2.0);
        String key = uniqueKey();

        RateLimitResult result = rateLimiter.tryAcquire(key, config);

        assertTrue(result.allowed(), "First request should be allowed");
        assertEquals(9, result.remainingTokens(), "Should have 9 remaining after consuming 1 of 10");
        assertEquals(0, result.retryAfterMillis(), "Retry-after should be 0 for allowed requests");
    }

    @Test
    @DisplayName("should reject request when bucket is exhausted")
    void shouldRejectRequestWhenBucketIsExhausted() {
        TokenBucketConfig config = new TokenBucketConfig(3, 1.0);
        String key = uniqueKey();

        // Exhaust all 3 tokens
        for (int i = 0; i < 3; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);
            assertTrue(result.allowed(), "Request " + (i + 1) + " should be allowed");
        }

        // 4th request should be rejected
        RateLimitResult rejected = rateLimiter.tryAcquire(key, config);
        assertFalse(rejected.allowed(), "4th request should be rejected");
        assertEquals(0, rejected.remainingTokens(), "No tokens should remain");
        assertTrue(rejected.retryAfterMillis() > 0, "Retry-after should be positive");
    }

    @Test
    @DisplayName("should refill tokens after elapsed time")
    void shouldRefillTokensAfterElapsedTime() throws InterruptedException {
        TokenBucketConfig config = new TokenBucketConfig(5, 10.0); // 10 tokens/sec = fast refill
        String key = uniqueKey();

        // Exhaust all tokens
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(key, config);
        }

        // Verify exhausted
        RateLimitResult exhausted = rateLimiter.tryAcquire(key, config);
        assertFalse(exhausted.allowed(), "Should be exhausted");

        // Wait for refill (at 10 tokens/sec, 500ms should give ~5 tokens)
        Thread.sleep(600);

        // Should be allowed again
        RateLimitResult refilled = rateLimiter.tryAcquire(key, config);
        assertTrue(refilled.allowed(), "Should be allowed after refill time");
    }

    @Test
    @DisplayName("should return correct retry-after when rejected")
    void shouldReturnCorrectRetryAfterMillis() {
        TokenBucketConfig config = new TokenBucketConfig(1, 1.0); // 1 token/sec
        String key = uniqueKey();

        // Consume the only token
        rateLimiter.tryAcquire(key, config);

        // Next request should have a retry-after ~1000ms
        RateLimitResult rejected = rateLimiter.tryAcquire(key, config);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterMillis() > 0,
                "Retry-after should be positive for rejected request");
        assertTrue(rejected.retryAfterMillis() <= 1100,
                "Retry-after should be approximately 1000ms, was: " + rejected.retryAfterMillis());
    }

    @Test
    @DisplayName("should never exceed max capacity after long idle")
    void shouldNeverExceedMaxCapacity() throws InterruptedException {
        TokenBucketConfig config = new TokenBucketConfig(5, 100.0); // Very fast refill
        String key = uniqueKey();

        // Initial request to create bucket
        rateLimiter.tryAcquire(key, config);

        // Wait long enough for massive theoretical refill
        Thread.sleep(500);

        // Should cap at maxCapacity (5), not accumulate beyond
        RateLimitResult result = rateLimiter.tryAcquire(key, config);
        assertTrue(result.allowed());
        assertTrue(result.remainingTokens() <= 5,
                "Remaining tokens should never exceed capacity, was: " + result.remainingTokens());
    }

    @Test
    @DisplayName("should handle first request on a new bucket correctly")
    void shouldHandleFirstRequestOnNewBucket() {
        TokenBucketConfig config = new TokenBucketConfig(100, 10.0);
        String key = uniqueKey();

        RateLimitResult result = rateLimiter.tryAcquire(key, config);

        assertTrue(result.allowed(), "First request on new bucket should always be allowed");
        assertEquals(99, result.remainingTokens(),
                "New bucket should start at maxCapacity minus cost");
    }

    @Test
    @DisplayName("should store state in Redis with correct key format")
    void shouldStoreStateInRedisWithCorrectKeyFormat() {
        TokenBucketConfig config = new TokenBucketConfig(10, 2.0);
        String logicalKey = "api:/login:192.168.1.1";

        rateLimiter.tryAcquire(logicalKey, config);

        // Verify key exists in Redis
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))) {
            String redisKey = "ratemaster:tokenbucket:" + logicalKey;
            assertTrue(jedis.exists(redisKey),
                    "Key should exist in Redis with the correct prefix");

            String tokens = jedis.hget(redisKey, "tokens");
            assertNotNull(tokens, "tokens field should be present in hash");

            String lastRefill = jedis.hget(redisKey, "last_refill");
            assertNotNull(lastRefill, "last_refill field should be present in hash");
        }
    }

    @Test
    @DisplayName("should reject null key")
    void shouldRejectNullKey() {
        TokenBucketConfig config = new TokenBucketConfig(10, 2.0);
        assertThrows(NullPointerException.class, () -> rateLimiter.tryAcquire(null, config));
    }

    @Test
    @DisplayName("should reject blank key")
    void shouldRejectBlankKey() {
        TokenBucketConfig config = new TokenBucketConfig(10, 2.0);
        assertThrows(IllegalArgumentException.class, () -> rateLimiter.tryAcquire("  ", config));
    }

    @Test
    @DisplayName("should reject null config")
    void shouldRejectNullConfig() {
        assertThrows(NullPointerException.class, () -> rateLimiter.tryAcquire("key", null));
    }
}
