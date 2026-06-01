package io.ratemaster.starter.spi;

import io.ratemaster.starter.annotation.RateLimit;
import io.ratemaster.starter.annotation.RateLimitFallback;
import io.ratemaster.starter.exception.RedisUnavailableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeRateLimiterFailureHandler implements RateLimiterFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(NativeRateLimiterFailureHandler.class);

    @Override
    public boolean handleFailure(Throwable ex, RateLimit rateLimit, ProceedingJoinPoint joinPoint) throws Exception {
        if (rateLimit.fallback() == RateLimitFallback.OPEN) {
            log.warn("RateMaster backend unavailable (fallback=OPEN). Bypassing rate limit for method: {}", joinPoint.getSignature().toShortString(), ex);
            return true;
        } else {
            log.error("RateMaster backend unavailable (fallback=CLOSED). Rejecting request for method: {}", joinPoint.getSignature().toShortString(), ex);
            throw new RedisUnavailableException("Rate limiter backend is unavailable and fallback is CLOSED.", ex);
        }
    }
}
