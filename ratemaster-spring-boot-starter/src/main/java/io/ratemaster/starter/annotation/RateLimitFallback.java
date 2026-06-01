package io.ratemaster.starter.annotation;

/**
 * Defines the fallback behavior when the underlying rate limiter storage (e.g., Redis)
 * is unavailable or times out.
 */
public enum RateLimitFallback {

    /**
     * Fail-open: If the rate limiter storage is unavailable, the request is allowed to pass.
     * This prevents the rate limiter from becoming a single point of failure that brings down the entire system.
     */
    OPEN,

    /**
     * Fail-closed: If the rate limiter storage is unavailable, the request is rejected.
     * This enforces strict limits at the cost of system availability during storage outages.
     */
    CLOSED
}
