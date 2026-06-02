package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.SlidingWindowConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;
import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SlidingWindowRateLimiterIT {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LuaScriptExecutor scriptExecutor;
    private SlidingWindowRateLimiter rateLimiter;
    private String uniqueKey;

    @BeforeAll
    static void setUpAll() {
        scriptExecutor = new JedisLuaScriptExecutor(redis.getHost(), redis.getFirstMappedPort());
    }

    @AfterAll
    static void tearDownAll() {
        // no-op
    }

    @BeforeEach
    void setUp() {
        rateLimiter = new SlidingWindowRateLimiter(scriptExecutor);
        uniqueKey = UUID.randomUUID().toString();
    }

    @Test
    void shouldAllowRequestsUpToCapacity() {
        SlidingWindowConfig config = new SlidingWindowConfig(3, 10);

        RateLimitResult r1 = rateLimiter.tryAcquire(uniqueKey, config);
        assertTrue(r1.allowed());
        assertEquals(2, r1.remainingTokens());

        RateLimitResult r2 = rateLimiter.tryAcquire(uniqueKey, config);
        assertTrue(r2.allowed());
        assertEquals(1, r2.remainingTokens());

        RateLimitResult r3 = rateLimiter.tryAcquire(uniqueKey, config);
        assertTrue(r3.allowed());
        assertEquals(0, r3.remainingTokens());

        RateLimitResult r4 = rateLimiter.tryAcquire(uniqueKey, config);
        assertFalse(r4.allowed());
        assertEquals(0, r4.remainingTokens());
        assertTrue(r4.retryAfterMillis() > 0);
    }

    @Test
    void shouldReleaseCapacityAfterWindowExpires() {
        SlidingWindowConfig config = new SlidingWindowConfig(1, 1);

        RateLimitResult r1 = rateLimiter.tryAcquire(uniqueKey, config);
        assertTrue(r1.allowed());

        RateLimitResult r2 = rateLimiter.tryAcquire(uniqueKey, config);
        assertFalse(r2.allowed());

        await().atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    RateLimitResult retry = rateLimiter.tryAcquire(uniqueKey, config);
                    assertTrue(retry.allowed(), "Should be allowed after window expires");
                });
    }
}
