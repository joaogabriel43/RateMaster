package io.ratemaster.starter.annotation;

import io.ratemaster.starter.resolver.IpKeyResolver;
import io.ratemaster.starter.resolver.RateLimitKeyResolver;

import java.lang.annotation.*;

/**
 * Declares that a method or all methods within a class are subject to rate limiting.
 *
 * <p>This annotation is intercepted by an AOP aspect to enforce limits using
 * the distributed Token Bucket algorithm.</p>
 *
 * <p>Values for {@code capacity} and {@code refillRate} must be positive (&gt; 0).
 * If invalid values are provided, an {@link IllegalArgumentException} will be thrown
 * at runtime on the first invocation.</p>
 *
 * @since 0.1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * The logical name of the rate limit bucket.
     * This forms the first part of the Redis key.
     *
     * @return the limit name
     */
    String name();

    /**
     * The algorithm used to enforce the rate limit.
     *
     * @return the algorithm
     */
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.TOKEN_BUCKET;

    /**
     * The maximum number of requests allowed.
     * This corresponds to burst capacity for Token Bucket,
     * and the total requests in the window for Sliding Window.
     *
     * @return the maximum number of requests/tokens
     */
    long capacity();

    /**
     * The window duration in seconds.
     * (Applicable only to SLIDING_WINDOW and FIXED_WINDOW).
     *
     * @return the window in seconds
     */
    long windowSeconds() default 60L;

    /**
     * The rate at which tokens are replenished, in tokens per second.
     * (Applicable only to TOKEN_BUCKET).
     *
     * @return the refill rate
     */
    double refillRate() default 1.0;

    /**
     * The resolver class used to extract the dynamic part of the key
     * (e.g., IP address, user ID, or method argument).
     *
     * @return the key resolver class
     */
    Class<? extends RateLimitKeyResolver> keyResolver() default IpKeyResolver.class;

    /**
     * A Spring Expression Language (SpEL) expression to evaluate when
     * {@link #keyResolver()} is set to {@code SpELKeyResolver.class}.
     *
     * @return the SpEL expression
     */
    String spelKey() default "";

    /**
     * The fallback behavior to execute if the rate limit storage becomes unavailable.
     *
     * @return the fallback strategy
     */
    RateLimitFallback fallback() default RateLimitFallback.OPEN;
}
