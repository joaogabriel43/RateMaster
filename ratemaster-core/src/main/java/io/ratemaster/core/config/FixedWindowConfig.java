package io.ratemaster.core.config;

/**
 * Configuration parameters for the Fixed Window algorithm.
 *
 * @param maxCapacity   the maximum number of requests allowed in the window
 * @param windowSeconds the duration of the window in seconds
 * @since 0.1.0
 */
public record FixedWindowConfig(long maxCapacity, long windowSeconds) {
    public FixedWindowConfig {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be > 0");
        }
    }
}
