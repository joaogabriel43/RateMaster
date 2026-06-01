package io.ratemaster.core.algorithm;

import io.ratemaster.core.adapter.JedisLuaScriptExecutor;
import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-threaded concurrency integration tests for {@link TokenBucketRateLimiter}.
 *
 * <p>These tests prove the absence of race conditions in the Token Bucket
 * algorithm under high concurrent load. Multiple threads simultaneously attempt
 * to acquire tokens from the same bucket, and the total number of granted
 * requests must not exceed the mathematical maximum.</p>
 *
 * <p>Each test is repeated 5 times ({@code @RepeatedTest(5)}) for statistical
 * confidence, as race conditions are non-deterministic by nature.</p>
 *
 * <h3>Concurrency Model</h3>
 * <p>Tests use a {@link CountDownLatch} to synchronize all threads, ensuring
 * they start their {@code tryAcquire} calls as close to simultaneously as
 * possible. Virtual threads (Java 21) are used for lightweight concurrency.</p>
 *
 * @since 0.1.0
 */
@Testcontainers
@DisplayName("Token Bucket Rate Limiter - Concurrency Tests")
class TokenBucketConcurrencyIT {

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
     * Proves that under simultaneous concurrent access from 50 threads,
     * the total number of allowed requests never exceeds the bucket capacity.
     *
     * <p>This test is the fundamental race-condition detector: if the Lua script's
     * atomicity were broken, we would observe more grants than the capacity allows.</p>
     *
     * <p>Mathematical proof: With a capacity of 10, refill rate of 1.0/sec, and
     * all 50 threads firing within milliseconds, the maximum allowed grants should
     * be at most {@code capacity + (elapsed_seconds * refillRate)}. Since elapsed
     * time is sub-second, the upper bound is approximately {@code capacity + 1}.</p>
     */
    @RepeatedTest(5)
    @DisplayName("should not exceed capacity under 50 concurrent threads")
    void shouldNotExceedCapacityUnderConcurrentLoad() throws InterruptedException {
        int threadCount = 50;
        long capacity = 10;
        double refillRate = 1.0;
        TokenBucketConfig config = new TokenBucketConfig(capacity, refillRate);
        String key = "concurrency:" + UUID.randomUUID();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads wait for the signal
                    RateLimitResult result = rateLimiter.tryAcquire(key, config);
                    if (result.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(threadCount, allowedCount.get() + rejectedCount.get(),
                "Total responses should equal thread count");

        // Upper bound: capacity + small tolerance for refill during execution
        long upperBound = capacity + 2;
        assertTrue(allowedCount.get() <= upperBound,
                "Allowed count (" + allowedCount.get() + ") must not exceed "
                        + "capacity + tolerance (" + upperBound + "). "
                        + "Rejected: " + rejectedCount.get());

        assertTrue(allowedCount.get() > 0, "At least some requests should be allowed");
    }

    /**
     * Proves that concurrent access never produces negative remaining token counts.
     *
     * <p>Each thread captures its {@link RateLimitResult} and all results are
     * verified post-execution to ensure the non-negativity invariant holds
     * under concurrent load.</p>
     */
    @RepeatedTest(5)
    @DisplayName("should never report negative remaining tokens under concurrent load")
    void shouldNeverReportNegativeRemainingTokens() throws InterruptedException {
        int threadCount = 30;
        TokenBucketConfig config = new TokenBucketConfig(5, 2.0);
        String key = "concurrency-neg:" + UUID.randomUUID();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<RateLimitResult> results = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = rateLimiter.tryAcquire(key, config);
                    results.add(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(threadCount, results.size(), "Should have results from all threads");

        for (int i = 0; i < results.size(); i++) {
            RateLimitResult result = results.get(i);
            assertTrue(result.remainingTokens() >= 0,
                    "Result " + i + ": remaining tokens must never be negative, was: "
                            + result.remainingTokens());
        }
    }

    /**
     * Stress test with multiple concurrent bursts separated by refill periods.
     *
     * <p>This test validates that the lazy refill mechanism works correctly
     * under concurrent access across multiple burst windows. The total allowed
     * requests across all windows must respect the mathematical throughput limit.</p>
     */
    @RepeatedTest(3)
    @DisplayName("should maintain correctness across multiple concurrent burst windows")
    void shouldMaintainCorrectnessAcrossMultipleBurstWindows() throws InterruptedException {
        int threadsPerBurst = 20;
        int burstCount = 3;
        long capacity = 5;
        double refillRate = 10.0; // Fast refill for test speed
        TokenBucketConfig config = new TokenBucketConfig(capacity, refillRate);
        String key = "concurrency-burst:" + UUID.randomUUID();

        AtomicInteger totalAllowed = new AtomicInteger(0);
        long testStartTime = System.currentTimeMillis();

        for (int burst = 0; burst < burstCount; burst++) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadsPerBurst);

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadsPerBurst; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        RateLimitResult result = rateLimiter.tryAcquire(key, config);
                        if (result.allowed()) {
                            totalAllowed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
                    "Burst " + burst + " threads should complete within timeout");
            executor.shutdown();

            // Wait for refill between bursts (except after last)
            if (burst < burstCount - 1) {
                Thread.sleep(600); // Allow ~6 tokens to refill at 10/sec
            }
        }

        long elapsedSeconds = (System.currentTimeMillis() - testStartTime) / 1000 + 1;
        // Maximum mathematical throughput: initial capacity + elapsed * refillRate
        long maxTheoreticalAllowed = capacity + (elapsedSeconds * (long) refillRate) + 5; // +5 tolerance

        assertTrue(totalAllowed.get() <= maxTheoreticalAllowed,
                "Total allowed across bursts (" + totalAllowed.get()
                        + ") should not exceed theoretical maximum (" + maxTheoreticalAllowed
                        + ") over " + elapsedSeconds + "s");
        assertTrue(totalAllowed.get() > 0, "At least some requests should be allowed");
    }
}
