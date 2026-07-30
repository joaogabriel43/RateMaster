package io.ratemaster.starter.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.util.concurrent.TimeUnit;

/**
 * L1 Cache (Two-Level Rate Limiting) acting as a Penalty Box.
 * 
 * <p>This component protects the Redis cluster by fasting-failing requests
 * that are already known to be blocked. It uses Caffeine for high-performance
 * bounded caching with precise time-to-live eviction based on the 
 * {@code retryAfterMillis} provided by Redis.</p>
 *
 * @since 0.1.0
 */
public class LocalPenaltyBox {

    private final Cache<String, Long> cache;

    public LocalPenaltyBox(long maxEntries) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfter(new Expiry<String, Long>() {
                    @Override
                    public long expireAfterCreate(String key, Long expirationTime, long currentTime) {
                        return calculateDurationNanos(expirationTime);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Long expirationTime, long currentTime, long currentDuration) {
                        return calculateDurationNanos(expirationTime);
                    }

                    @Override
                    public long expireAfterRead(String key, Long expirationTime, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long calculateDurationNanos(Long expirationTime) {
                        long durationMillis = expirationTime - System.currentTimeMillis();
                        return durationMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(durationMillis) : 0;
                    }
                })
                .build();
    }

    /**
     * Penalizes a client by placing them in the local cache.
     *
     * @param key              the logical client key
     * @param retryAfterMillis the duration of the penalty
     */
    public void penalize(String key, long retryAfterMillis) {
        if (retryAfterMillis > 0) {
            cache.put(key, System.currentTimeMillis() + retryAfterMillis);
        }
    }

    /**
     * Checks if a client is currently penalized.
     *
     * @param key the logical client key
     * @return true if penalized, false otherwise
     */
    public boolean isPenalized(String key) {
        Long expiration = cache.getIfPresent(key);
        if (expiration == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiration) {
            cache.invalidate(key);
            return false;
        }
        return true;
    }

    /**
     * Returns the remaining penalty time in milliseconds.
     *
     * @param key the logical client key
     * @return the remaining time in millis, or 0 if not penalized
     */
    public long getPenaltyRemainingMillis(String key) {
        Long expiration = cache.getIfPresent(key);
        if (expiration == null) {
            return 0;
        }
        long remaining = expiration - System.currentTimeMillis();
        if (remaining <= 0) {
            cache.invalidate(key);
            return 0;
        }
        return remaining;
    }
}
