package io.ratemaster.starter.resolver;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Key resolver that extracts the client's IP address from the HTTP request.
 *
 * <p>It uses {@link RequestContextHolder} to access the thread-bound request.
 * It also checks the {@code X-Forwarded-For} header for environments behind proxies.</p>
 *
 * @since 0.1.0
 */
public class IpKeyResolver implements RateLimitKeyResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    @Override
    public String resolveKey(MethodInvocation invocation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return UNKNOWN;
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader(X_FORWARDED_FOR);
        
        if (ip == null || ip.isBlank() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            // X-Forwarded-For can contain multiple IPs, the first one is the original client
            ip = ip.split(",")[0].trim();
        }

        return ip != null && !ip.isBlank() ? ip : UNKNOWN;
    }
}
