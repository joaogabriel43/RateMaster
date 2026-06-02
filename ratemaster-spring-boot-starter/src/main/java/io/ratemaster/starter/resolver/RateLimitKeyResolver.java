package io.ratemaster.starter.resolver;

import org.aopalliance.intercept.MethodInvocation;

/**
 * SPI for resolving rate limit keys dynamically from method invocations.
 *
 * <p>Implementations can extract keys from the current HTTP request (e.g., IP address,
 * headers), the authenticated user, or the method arguments themselves (e.g., via SpEL).
 * The returned values are automatically sanitized by the framework to prevent Redis key injection.</p>
 *
 * @since 0.1.0
 */
public interface RateLimitKeyResolver {

    /**
     * Resolves the rate limit key for the given method invocation.
     *
     * @param invocation the method invocation intercepted by the AOP aspect
     * @return the resolved key, or a fallback value if resolution fails
     */
    String resolveKey(MethodInvocation invocation);
}
