package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.SlidingWindowConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;
import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowPropertyIT {

    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LuaScriptExecutor scriptExecutor;
    private static SlidingWindowRateLimiter rateLimiter;

    @BeforeContainer
    static void setUp() {
        redis.start();
        scriptExecutor = new JedisLuaScriptExecutor(redis.getHost(), redis.getFirstMappedPort());
        rateLimiter = new SlidingWindowRateLimiter(scriptExecutor);
    }

    @AfterContainer
    static void tearDown() {
        redis.stop();
    }

    @Property(tries = 50)
    void capacityNeverExceeded(@ForAll("capacities") long capacity) {
        String key = UUID.randomUUID().toString();
        SlidingWindowConfig config = new SlidingWindowConfig(capacity, 60);

        int allowedCount = 0;
        for (int i = 0; i < capacity + 5; i++) {
            RateLimitResult result = rateLimiter.tryAcquire(key, config);
            if (result.allowed()) {
                allowedCount++;
            }
        }

        assertTrue(allowedCount <= capacity, "Allowed count should never exceed capacity");
    }

    @Provide
    Arbitrary<Long> capacities() {
        return Arbitraries.longs().between(1, 100);
    }
}
