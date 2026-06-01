package io.ratemaster.starter.aop;

import io.ratemaster.core.algorithm.TokenBucketRateLimiter;
import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.starter.annotation.RateLimit;
import io.ratemaster.starter.exception.RateLimitExceededException;
import io.ratemaster.starter.resolver.RateLimitKeyResolver;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AOP Aspect that intercepts methods annotated with {@link RateLimit}
 * to enforce distributed rate limits.
 *
 * <p>This aspect extracts the configuration from the annotation, resolves the
 * dynamic portion of the key using the specified {@link RateLimitKeyResolver},
 * and delegates to the core {@link TokenBucketRateLimiter} for evaluation.</p>
 *
 * @since 0.1.0
 */
@Aspect
public class RateLimitAspect {

    private final TokenBucketRateLimiter rateLimiter;
    private final ApplicationContext applicationContext;
    private final ConcurrentMap<Class<? extends RateLimitKeyResolver>, RateLimitKeyResolver> resolverCache = new ConcurrentHashMap<>();

    /**
     * Creates a new aspect instance.
     *
     * @param rateLimiter        the core rate limiter; must not be null
     * @param applicationContext the Spring application context used to fetch key resolvers; must not be null
     */
    public RateLimitAspect(TokenBucketRateLimiter rateLimiter, ApplicationContext applicationContext) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
    }

    /**
     * Intercepts executions of methods annotated with {@code @RateLimit}.
     *
     * @param joinPoint the AOP join point
     * @param rateLimit the annotation instance
     * @return the result of the intercepted method if allowed
     * @throws Throwable if the intercepted method throws an exception, or a
     *                   {@link RateLimitExceededException} if the limit is exceeded
     */
    @Around("@annotation(rateLimit)")
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        
        // Build the TokenBucketConfig from annotation attributes
        TokenBucketConfig config = new TokenBucketConfig(rateLimit.capacity(), rateLimit.refillRate());

        // Resolve the dynamic part of the key
        RateLimitKeyResolver resolver = getResolver(rateLimit.keyResolver());
        MethodInvocation invocation = getMethodInvocation(joinPoint);
        String resolvedKey = resolver.resolveKey(invocation);

        // Combine logical name with resolved dynamic key (e.g., "demoApi:127.0.0.1")
        // The core will prepend "ratemaster:tokenbucket:" internally.
        String logicalKey = rateLimit.name() + ":" + resolvedKey;

        // Evaluate request against the core logic
        RateLimitResult result = rateLimiter.tryAcquire(logicalKey, config);

        if (result.allowed()) {
            return joinPoint.proceed();
        } else {
            throw new RateLimitExceededException(
                    "Rate limit exceeded for bucket: " + rateLimit.name(),
                    result.retryAfterMillis()
            );
        }
    }

    /**
     * Fetches or instantiates the required key resolver.
     *
     * <p>First attempts to find it as a Spring Bean. If not present, creates
     * a new instance and caches it.</p>
     */
    private RateLimitKeyResolver getResolver(Class<? extends RateLimitKeyResolver> clazz) {
        return resolverCache.computeIfAbsent(clazz, type -> {
            try {
                return applicationContext.getBean(type);
            } catch (Exception e) {
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw new IllegalStateException("Could not instantiate KeyResolver: " + type.getName(), ex);
                }
            }
        });
    }

    /**
     * Adapts an AspectJ ProceedingJoinPoint to an AOP Alliance MethodInvocation
     * required by the KeyResolver SPI.
     */
    private MethodInvocation getMethodInvocation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return pjp.getArgs();
            }

            @Override
            public Object proceed() throws Throwable {
                return pjp.proceed();
            }

            @Override
            public Object getThis() {
                return pjp.getTarget();
            }

            @Override
            public java.lang.reflect.AccessibleObject getStaticPart() {
                return method;
            }
        };
    }
}
