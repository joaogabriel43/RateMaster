package io.ratemaster.starter.exception;

/**
 * Exception thrown when a rate limit is exceeded.
 *
 * <p>This exception is typically caught by a global exception handler
 * (like {@code @RestControllerAdvice}) to return an HTTP 429 status code
 * along with a {@code Retry-After} header.</p>
 *
 * @since 0.1.0
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMillis;

    /**
     * Creates a new instance.
     *
     * @param message          the exception message
     * @param retryAfterMillis the suggested wait time in milliseconds before retrying
     */
    public RateLimitExceededException(String message, long retryAfterMillis) {
        super(message);
        this.retryAfterMillis = retryAfterMillis;
    }

    /**
     * Returns the suggested wait time in milliseconds before retrying.
     *
     * @return the retry-after duration in milliseconds
     */
    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
