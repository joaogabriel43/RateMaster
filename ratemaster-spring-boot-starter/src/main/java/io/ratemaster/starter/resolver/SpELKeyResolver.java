package io.ratemaster.starter.resolver;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import io.ratemaster.starter.annotation.RateLimit;

import java.lang.reflect.Method;

/**
 * Key resolver that evaluates a Spring Expression Language (SpEL) string
 * against the method's arguments.
 *
 * <p>Requires compilation with the {@code -parameters} flag (default in Boot 4.0)
 * to resolve method argument names.</p>
 *
 * @since 0.1.0
 */
public class SpELKeyResolver implements RateLimitKeyResolver {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Override
    public String resolveKey(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        
        if (rateLimit == null) {
            return "unknown";
        }
        
        String spelExpression = rateLimit.spelKey();
        if (spelExpression.isBlank()) {
            throw new IllegalArgumentException("spelKey must not be blank when using SpELKeyResolver");
        }

        EvaluationContext context = new MethodBasedEvaluationContext(
                invocation.getThis(),
                method,
                invocation.getArguments(),
                nameDiscoverer
        );

        Object value = parser.parseExpression(spelExpression).getValue(context);
        return value != null ? value.toString() : "null";
    }
}
