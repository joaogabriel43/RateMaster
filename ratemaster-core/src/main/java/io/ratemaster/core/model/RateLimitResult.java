package io.ratemaster.core.model;

/**
 * Represents the outcome of a rate limit evaluation against a Token Bucket.
 *
 * <p>Encapsulates three key pieces of information:</p>
 * <ul>
 *   <li><b>allowed:</b> Whether the request was permitted (a token was consumed).</li>
 *   <li><b>remainingTokens:</b> The number of tokens remaining in the bucket after
 *       this evaluation. Always non-negative.</li>
 *   <li><b>retryAfterMillis:</b> If the request was rejected, the estimated time in
 *       milliseconds until enough tokens will be available. Zero if allowed.</li>
 * </ul>
 *
 * @param allowed          {@code true} if the request was permitted; {@code false} if rate-limited
 * @param remainingTokens  the number of tokens remaining in the bucket; always {@code >= 0}
 * @param retryAfterMillis estimated wait time in milliseconds before retrying; {@code 0} if allowed
 * @since 0.1.0
 */
public record RateLimitResult(boolean allowed, long remainingTokens, long retryAfterMillis) {

    /**
     * Creates a result indicating the request was allowed.
     *
     * @param remainingTokens the number of tokens remaining after consumption
     * @return an allowed {@code RateLimitResult}
     */
    public static RateLimitResult allowed(long remainingTokens) {
        return new RateLimitResult(true, remainingTokens, 0);
    }

    /**
     * Creates a result indicating the request was rejected due to rate limiting.
     *
     * @param remainingTokens  the number of tokens remaining (insufficient for the request)
     * @param retryAfterMillis the estimated wait time in milliseconds before retrying
     * @return a rejected {@code RateLimitResult}
     */
    public static RateLimitResult rejected(long remainingTokens, long retryAfterMillis) {
        return new RateLimitResult(false, remainingTokens, retryAfterMillis);
    }
}
