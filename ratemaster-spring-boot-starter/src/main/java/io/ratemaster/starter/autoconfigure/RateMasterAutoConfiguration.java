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

import io.ratemaster.starter.spi.RateLimiterFailureHandler;
import io.ratemaster.starter.spi.NativeRateLimiterFailureHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Auto-configuration for RateMaster.
 *
 * <p>Registers the necessary beans to enable distributed rate limiting via the
 * {@code @RateLimit} annotation using Redis.</p>
 *
 * @since 0.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RateMasterProperties.class)
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
    public RateLimiterFailureHandler rateLimiterFailureHandler() {
        return new NativeRateLimiterFailureHandler();
    }

    @Bean(name = "rateMasterExecutor")
    @ConditionalOnMissingBean(name = "rateMasterExecutor")
    public Executor rateMasterExecutor(RateMasterProperties properties, Environment env) {
        Boolean virtualThreadsEnabled = env.getProperty("spring.threads.virtual.enabled", Boolean.class, false);
        if (Boolean.TRUE.equals(virtualThreadsEnabled)) {
            return Executors.newVirtualThreadPerTaskExecutor();
        } else {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(properties.getExecutor().getCoreSize());
            executor.setMaxPoolSize(properties.getExecutor().getMaxSize());
            executor.setThreadNamePrefix("ratemaster-");
            executor.initialize();
            return executor;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(
            TokenBucketRateLimiter tokenBucketRateLimiter,
            ApplicationContext applicationContext,
            RateMasterProperties properties,
            RateLimiterFailureHandler failureHandler,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("rateMasterExecutor") Executor rateMasterExecutor) {
        return new RateLimitAspect(
                tokenBucketRateLimiter, 
                applicationContext, 
                properties, 
                failureHandler, 
                meterRegistryProvider, 
                rateMasterExecutor
        );
    }
}
