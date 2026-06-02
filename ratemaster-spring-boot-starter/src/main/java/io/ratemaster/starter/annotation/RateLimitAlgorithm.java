package io.ratemaster.starter.annotation;

/**
 * Defines the available rate limiting algorithms supported by RateMaster.
 *
 * @since 0.1.0
 */
public enum RateLimitAlgorithm {
    
    /**
     * The Token Bucket algorithm. Allows bursts up to a defined capacity,
     * refilling tokens at a constant rate.
     */
    TOKEN_BUCKET,

    /**
     * The Sliding Window algorithm. Provides precise rate limiting across
     * a continuous time window, preventing boundary burst effects.
     */
    SLIDING_WINDOW
}
