package io.ratemaster.core.config;

/**
 * Immutable configuration for a Token Bucket rate limiter instance.
 *
 * <p>Defines the two fundamental parameters of the Token Bucket algorithm:</p>
 * <ul>
 *   <li><b>maxCapacity:</b> The maximum number of tokens the bucket can hold.
 *       This determines the burst size — the maximum number of requests that can
 *       be served instantaneously before throttling begins.</li>
 *   <li><b>refillRatePerSecond:</b> The rate at which tokens are replenished,
 *       expressed as tokens per second. This controls the sustained throughput
 *       over time.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // 10 requests burst, 2 requests/sec sustained
 * var config = new TokenBucketConfig(10, 2.0);
 * }</pre>
 *
 * @param maxCapacity         the maximum number of tokens the bucket can hold; must be greater than zero
 * @param refillRatePerSecond the rate of token replenishment in tokens per second; must be greater than zero
 * @since 0.1.0
 */
public record TokenBucketConfig(long maxCapacity, double refillRatePerSecond) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if {@code maxCapacity} is less than or equal to zero
     * @throws IllegalArgumentException if {@code refillRatePerSecond} is less than or equal to zero
     *                                  or is not a finite number
     */
    public TokenBucketConfig {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException(
                    "maxCapacity must be greater than zero, but was: " + maxCapacity);
        }
        if (refillRatePerSecond <= 0 || !Double.isFinite(refillRatePerSecond)) {
            throw new IllegalArgumentException(
                    "refillRatePerSecond must be a positive finite number, but was: " + refillRatePerSecond);
        }
    }
}
