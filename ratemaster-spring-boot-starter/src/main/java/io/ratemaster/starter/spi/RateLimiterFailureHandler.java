package io.ratemaster.starter.spi;

import io.ratemaster.starter.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * SPI for handling rate limiter infrastructure failures (e.g., Redis timeouts or connection errors).
 *
 * <p>This interface serves as an extension point for consumers who wish to plug in
 * sophisticated circuit breakers (e.g., Resilience4j) without requiring the core
 * RateMaster library to depend on them.</p>
 */
public interface RateLimiterFailureHandler {

    /**
     * Handles the failure of a rate limit evaluation.
     *
     * @param ex        the underlying exception (e.g., TimeoutException or RedisConnectionException)
     * @param rateLimit the annotation specifying the configured fallback strategy
     * @param joinPoint the intercepted method execution
     * @return {@code true} if the request should be allowed (fail-open), or throws an exception (fail-closed)
     * @throws Exception if the request should be rejected due to the failure
     */
    boolean handleFailure(Throwable ex, RateLimit rateLimit, ProceedingJoinPoint joinPoint) throws Exception;
}
