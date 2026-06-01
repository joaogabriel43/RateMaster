package io.ratemaster.starter.web;

import io.ratemaster.starter.exception.RateLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;

/**
 * Global exception handler for rate limit violations.
 *
 * <p>Translates {@link RateLimitExceededException} into HTTP 429 (Too Many Requests)
 * responses, including the standard {@code Retry-After} header (in seconds, as per RFC 9110).</p>
 *
 * <p>Only loaded if the application is a web application.</p>
 *
 * @since 0.1.0
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
        
        // Convert milliseconds to seconds, rounding up to ensure the client waits long enough
        long retryAfterSeconds = (long) Math.ceil(ex.getRetryAfterMillis() / 1000.0);
        
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        Map<String, String> body = Collections.singletonMap("error", "Too Many Requests");

        return new ResponseEntity<>(body, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(io.ratemaster.starter.exception.RedisUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleRedisUnavailable(io.ratemaster.starter.exception.RedisUnavailableException ex) {
        Map<String, String> body = Collections.singletonMap("error", "Service Unavailable");
        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
