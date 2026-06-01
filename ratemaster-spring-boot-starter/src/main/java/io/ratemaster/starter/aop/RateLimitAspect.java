package io.ratemaster.starter.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.ratemaster.core.algorithm.TokenBucketRateLimiter;
import io.ratemaster.core.config.TokenBucketConfig;
import io.ratemaster.core.model.RateLimitResult;
import io.ratemaster.starter.annotation.RateLimit;
import io.ratemaster.starter.autoconfigure.RateMasterProperties;
import io.ratemaster.starter.exception.RateLimitExceededException;
import io.ratemaster.starter.exception.RedisUnavailableException;
import io.ratemaster.starter.resolver.RateLimitKeyResolver;
import io.ratemaster.starter.spi.RateLimiterFailureHandler;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * AOP Aspect that intercepts methods annotated with {@link RateLimit}
 * to enforce distributed rate limits.
 *
 * @since 0.1.0
 */
@Aspect
public class RateLimitAspect {

    private final TokenBucketRateLimiter rateLimiter;
    private final ApplicationContext applicationContext;
    private final RateMasterProperties properties;
    private final RateLimiterFailureHandler failureHandler;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Executor executor;
    
    private final ConcurrentMap<Class<? extends RateLimitKeyResolver>, RateLimitKeyResolver> resolverCache = new ConcurrentHashMap<>();

    public RateLimitAspect(
            TokenBucketRateLimiter rateLimiter, 
            ApplicationContext applicationContext,
            RateMasterProperties properties,
            RateLimiterFailureHandler failureHandler,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            Executor executor) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler must not be null");
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider, "meterRegistryProvider must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Around("@annotation(rateLimit)")
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        
        TokenBucketConfig config = new TokenBucketConfig(rateLimit.capacity(), rateLimit.refillRate());

        RateLimitKeyResolver resolver = getResolver(rateLimit.keyResolver());
        MethodInvocation invocation = getMethodInvocation(joinPoint);
        String resolvedKey = resolver.resolveKey(invocation);

        String logicalKey = rateLimit.name() + ":" + resolvedKey;

        RateLimitResult result;
        try {
            result = CompletableFuture.supplyAsync(() -> rateLimiter.tryAcquire(logicalKey, config), executor)
                    .orTimeout(properties.getRedis().getCommandTimeoutMs(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (Exception ex) {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            
            try {
                boolean fallbackAllowed = failureHandler.handleFailure(cause, rateLimit, joinPoint);
                if (fallbackAllowed) {
                    recordMetric("ratemaster.requests.allowed", rateLimit.name(), resolvedKey, null);
                    return joinPoint.proceed();
                } else {
                    recordMetric("ratemaster.requests.rejected", rateLimit.name(), resolvedKey, "REDIS_FALLBACK_CLOSED");
                    throw new RedisUnavailableException("Rate limiter backend is unavailable and fallback is CLOSED.", cause);
                }
            } catch (Exception fallbackEx) {
                recordMetric("ratemaster.requests.rejected", rateLimit.name(), resolvedKey, "REDIS_FALLBACK_CLOSED");
                throw fallbackEx;
            }
        }

        if (result.allowed()) {
            recordMetric("ratemaster.requests.allowed", rateLimit.name(), resolvedKey, null);
            return joinPoint.proceed();
        } else {
            recordMetric("ratemaster.requests.rejected", rateLimit.name(), resolvedKey, "RATE_LIMIT");
            throw new RateLimitExceededException(
                    "Rate limit exceeded for bucket: " + rateLimit.name(),
                    result.retryAfterMillis()
            );
        }
    }

    private void recordMetric(String metricName, String limitName, String clientKey, String reason) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            Tags tags = Tags.of("limitName", limitName, "clientKey", clientKey);
            if (reason != null) {
                tags = tags.and("reason", reason);
            }
            registry.counter(metricName, tags).increment();
        }
    }

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
