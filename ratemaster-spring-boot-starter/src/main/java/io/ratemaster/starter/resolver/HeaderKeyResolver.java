package io.ratemaster.starter.resolver;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

/**
 * Key resolver that extracts a specific HTTP header from the request.
 *
 * @since 0.1.0
 */
public class HeaderKeyResolver implements RateLimitKeyResolver {

    private final String headerName;

    public HeaderKeyResolver(String headerName) {
        this.headerName = Objects.requireNonNull(headerName, "headerName must not be null");
    }

    @Override
    public String resolveKey(MethodInvocation invocation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        String headerValue = attributes.getRequest().getHeader(headerName);
        return (headerValue != null && !headerValue.isBlank()) ? headerValue : "unknown";
    }
}
