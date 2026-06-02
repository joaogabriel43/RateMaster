# RateMaster

RateMaster is a high-performance, distributed rate-limiting library for Spring Boot 4.0.x and Java 21, built on Redis. It provides a lightweight, resilient token bucket algorithm with no external circuit breaker dependencies.

## Known Limitations

### AOP Self-Invocation
`@RateLimit` is enforced via Spring AOP proxies. If a Spring bean calls an annotated 
method on itself (self-invocation) without going through the proxy, the rate limit 
**will not be applied**.

**Mitigation options:**
1. Inject the bean into itself via `@Lazy`: `@Autowired @Lazy MyService self;` and call `self.myMethod()`.
2. Use `AopContext.currentProxy()` with `@EnableAspectJAutoProxy(exposeProxy = true)`.
3. Restructure to call the annotated method from a different bean.

This is a well-known Spring AOP limitation. For compile-time weaving (no self-invocation blind spot), use AspectJ LTW with `spring-boot-starter-aop` + the AspectJ agent.

## Configuration

> **⚠️ Important: align Redis driver timeout with RateMaster timeout**  
> `CompletableFuture.orTimeout()` returns control to the caller fast, but the underlying 
> Redis operation continues on the `rateMasterExecutor` thread until the driver-level 
> timeout fires. **Always configure:**
> ```yaml
> spring.data.redis.timeout: 400ms       # ≤ ratemaster.redis.command-timeout-ms
> ratemaster.redis.command-timeout-ms: 500
> ```
> Without this, sustained Redis instability can exhaust the executor thread pool 
> (especially on the platform thread fallback when Virtual Threads are disabled).
