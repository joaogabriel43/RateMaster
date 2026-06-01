package io.ratemaster.starter.autoconfigure;

import io.ratemaster.core.algorithm.TokenBucketRateLimiter;
import io.ratemaster.core.port.LuaScriptExecutor;
import io.ratemaster.starter.adapter.SpringDataRedisScriptExecutor;
import io.ratemaster.starter.aop.RateLimitAspect;
import io.ratemaster.starter.resolver.IpKeyResolver;
import io.ratemaster.starter.web.RateLimitExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Auto-configuration for RateMaster.
 *
 * <p>Registers the necessary beans to enable distributed rate limiting via the
 * {@code @RateLimit} annotation using Redis.</p>
 *
 * @since 0.1.0
 */
@AutoConfiguration
@Import(RateLimitExceptionHandler.class)
public class RateMasterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> tokenBucketRedisScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    @ConditionalOnMissingBean(LuaScriptExecutor.class)
    @SuppressWarnings("unchecked")
    public SpringDataRedisScriptExecutor springDataRedisScriptExecutor(
            StringRedisTemplate stringRedisTemplate,
            RedisScript<List> tokenBucketRedisScript) {
        return new SpringDataRedisScriptExecutor(stringRedisTemplate, tokenBucketRedisScript);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBucketRateLimiter tokenBucketRateLimiter(LuaScriptExecutor luaScriptExecutor) {
        return new TokenBucketRateLimiter(luaScriptExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public IpKeyResolver ipKeyResolver() {
        return new IpKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(
            TokenBucketRateLimiter tokenBucketRateLimiter,
            ApplicationContext applicationContext) {
        return new RateLimitAspect(tokenBucketRateLimiter, applicationContext);
    }
}
