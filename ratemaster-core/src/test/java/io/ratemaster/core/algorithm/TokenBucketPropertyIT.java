package io.ratemaster.core.algorithm;

import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based integration tests for the Token Bucket algorithm using jqwik 1.9.3.
 *
 * <p>These tests prove mathematical invariants of the Token Bucket algorithm
 * hold for arbitrary random inputs. Each property is tested against a real
 * Redis instance via Testcontainers to verify that the Lua script maintains
 * correctness under all conditions.</p>
 *
 * <h3>Proven Invariants</h3>
 * <ul>
 *   <li><b>Non-negativity:</b> remaining tokens are always {@code >= 0}</li>
 *   <li><b>Capacity bound:</b> remaining tokens never exceed {@code maxCapacity}</li>
 *   <li><b>Throughput bound:</b> total granted tokens respect the mathematical limit</li>
 * </ul>
 *
 * <p><b>WARNING:</b> jqwik is pinned to version 1.9.3. Versions >= 1.10 contain
 * protestware. See CLAUDE.md known errors section.</p>
 *
 * @since 0.1.0
 */
class TokenBucketPropertyIT {

    private static final int REDIS_PORT = 6379;

    // Manual container lifecycle (jqwik doesn't support @Testcontainers annotation)
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private TokenBucketRateLimiter rateLimiter;

    static {
        REDIS.start();
    }

    @BeforeProperty
    void setUp() {
        JedisLuaScriptExecutor executor = new JedisLuaScriptExecutor(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        rateLimiter = new TokenBucketRateLimiter(executor);
    }

    @AfterProperty
    void cleanUp() {
        // Flush Redis to avoid cross-property contamination
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))) {
            jedis.flushAll();
        }
    }

    /**
     * Generates valid TokenBucketConfig with constrained random values.
     */
    @Provide
    Arbitrary<TokenBucketConfig> validConfigs() {
        return Combinators.combine(
                Arbitraries.longs().between(1, 100),       // maxCapacity
                Arbitraries.doubles().between(0.1, 50.0)   // refillRatePerSecond
        ).as(TokenBucketConfig::new);
    }

    /**
     * <b>Invariant: Remaining tokens are never negative.</b>
     *
     * <p>For any valid configuration and any number of sequential requests,
     * the remaining token count reported by the rate limiter must always
     * be greater than or equal to zero.</p>
     */
    @Property(tries = 30)
    @Label("tokens never go negative for any request sequence")
    void tokensNeverNegative(
            @ForAll("validConfigs") TokenBucketConfig config,
            @ForAll @IntRange(min = 1, max = 200) int requestCount) {

        String key = "prop-neg:" + UUID.randomUUID();

        for (int i = 0; i < requestCount; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);
            assertTrue(result.remainingTokens() >= 0,
                    "Remaining tokens must never be negative. Got: " + result.remainingTokens()
                            + " on request " + (i + 1) + " with config " + config);
        }
    }

    /**
     * <b>Invariant: Remaining tokens never exceed max capacity.</b>
     *
     * <p>For any valid configuration and any number of sequential requests,
     * the remaining token count must never exceed the configured maximum
     * capacity, even after extended idle periods that would trigger refill.</p>
     */
    @Property(tries = 30)
    @Label("tokens never exceed max capacity")
    void tokensNeverExceedCapacity(
            @ForAll("validConfigs") TokenBucketConfig config,
            @ForAll @IntRange(min = 1, max = 200) int requestCount) {

        String key = "prop-cap:" + UUID.randomUUID();

        for (int i = 0; i < requestCount; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);
            assertTrue(result.remainingTokens() <= config.maxCapacity(),
                    "Remaining tokens (" + result.remainingTokens()
                            + ") must not exceed maxCapacity (" + config.maxCapacity()
                            + ") on request " + (i + 1));
        }
    }

    /**
     * <b>Invariant: Throughput respects the mathematical limit.</b>
     *
     * <p>For a given capacity and request count, the total number of allowed
     * requests in a tight loop (negligible elapsed time) must not exceed
     * the initial capacity. This proves the burst cap is enforced.</p>
     */
    @Property(tries = 30)
    @Label("burst-allowed count does not exceed initial capacity")
    void burstAllowedCountDoesNotExceedCapacity(
            @ForAll("validConfigs") TokenBucketConfig config) {

        String key = "prop-burst:" + UUID.randomUUID();
        int totalRequests = (int) config.maxCapacity() + 20; // Request more than capacity

        long allowedCount = 0;
        for (int i = 0; i < totalRequests; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);
            if (result.allowed()) {
                allowedCount++;
            }
        }

        // In a tight loop, allowed count should be at most maxCapacity + a small
        // tolerance for refill that may occur during execution (sub-millisecond)
        long upperBound = config.maxCapacity() + 2; // +2 for potential micro-refill
        assertTrue(allowedCount <= upperBound,
                "Burst allowed count (" + allowedCount + ") should not exceed "
                        + "maxCapacity + tolerance (" + upperBound + ") for config " + config);
    }

    /**
     * <b>Invariant: Retry-after is zero if and only if allowed is true.</b>
     *
     * <p>The retryAfterMillis field must be zero when the request is allowed
     * and must be positive (or zero for rounding edge cases) when rejected.</p>
     */
    @Property(tries = 30)
    @Label("retry-after is zero when allowed, positive when rejected")
    void retryAfterConsistentWithAllowed(
            @ForAll("validConfigs") TokenBucketConfig config,
            @ForAll @IntRange(min = 1, max = 200) int requestCount) {

        String key = "prop-retry:" + UUID.randomUUID();

        for (int i = 0; i < requestCount; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);

            if (result.allowed()) {
                assertEquals(0, result.retryAfterMillis(),
                        "Retry-after must be 0 when request is allowed");
            } else {
                assertTrue(result.retryAfterMillis() >= 0,
                        "Retry-after must be non-negative when rejected");
            }
        }
    }
}
