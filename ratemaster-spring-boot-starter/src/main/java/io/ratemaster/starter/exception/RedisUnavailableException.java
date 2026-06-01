package io.ratemaster.starter.exception;

/**
 * Exception thrown when the rate limiter backend (Redis) is unavailable,
 * times out, or fails, and the fallback strategy is set to CLOSED.
 *
 * <p>This allows global exception handlers to map the error to a HTTP 503
 * Service Unavailable response.</p>
 *
 * @since 0.1.0
 */
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
