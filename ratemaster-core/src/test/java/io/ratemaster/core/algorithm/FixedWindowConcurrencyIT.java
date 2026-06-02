package io.ratemaster.core.algorithm;

import io.ratemaster.core.config.FixedWindowConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.core.port.LuaScriptExecutor;
import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class FixedWindowConcurrencyIT {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LuaScriptExecutor scriptExecutor;
    private static FixedWindowRateLimiter rateLimiter;

    @BeforeAll
    static void setUpAll() {
        scriptExecutor = new JedisLuaScriptExecutor(redis.getHost(), redis.getFirstMappedPort());
        rateLimiter = new FixedWindowRateLimiter(scriptExecutor);
    }

    @AfterAll
    static void tearDownAll() {
        // no-op
    }

    @RepeatedTest(5)
    void shouldHandleConcurrentRequestsStrictly() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        long capacity = 20;
        // Large window to avoid boundary crossings during test
        FixedWindowConfig config = new FixedWindowConfig(capacity, 60);

        int threadCount = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RateLimitResult result = rateLimiter.tryAcquire(key, config);
                    if (result.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(capacity, allowedCount.get(), "Exactly capacity requests should be allowed");
        assertEquals(threadCount - capacity, rejectedCount.get(), "Remaining requests should be rejected");
    }
}
