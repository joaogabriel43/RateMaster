package io.ratemaster.starter.resolver;

/**
 * Utility class for sanitizing keys resolved from clients to prevent Redis key injection.
 *
 * @since 0.1.0
 */
public final class RateLimitKeyUtils {

    private RateLimitKeyUtils() {
        // utility class
    }

    /**
     * Sanitizes the given key by replacing ':' with '-' and removing restricted characters
     * such as '{', '}', '*', and '?'.
     *
     * @param key the original resolved key
     * @return the sanitized key safe for Redis usage
     */
    public static String sanitize(String key) {
        if (key == null) {
            return null;
        }
        return key.replace(':', '-')
                  .replace("{", "")
                  .replace("}", "")
                  .replace("*", "")
                  .replace("?", "")
                  .trim();
    }
}
