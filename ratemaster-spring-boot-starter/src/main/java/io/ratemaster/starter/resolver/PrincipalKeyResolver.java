package io.ratemaster.starter.resolver;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;

/**
 * Key resolver that extracts the authenticated user's principal name.
 *
 * @since 0.1.0
 */
public class PrincipalKeyResolver implements RateLimitKeyResolver {

    @Override
    public String resolveKey(MethodInvocation invocation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }

        HttpServletRequest request = attributes.getRequest();
        Principal principal = request.getUserPrincipal();
        
        return principal != null ? principal.getName() : "anonymous";
    }
}
