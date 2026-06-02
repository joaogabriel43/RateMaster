# RateMaster

[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub Packages](https://img.shields.io/badge/GitHub-Packages-blueviolet)](https://github.com/joaogabriel43/RateMaster/packages)

High-performance, distributed rate-limiting library for Spring Boot, built on Redis. RateMaster provides a lightweight, resilient token bucket algorithm with atomic Lua script execution, guaranteeing zero race conditions across distributed microservices—vastly superior to in-memory locks. Seamlessly declarative via Spring AOP annotations.

---

## Algorithms Status

| Algorithm | Status | Redis Structure | Best For |
| :--- | :--- | :--- | :--- |
| **Token Bucket** | `v1.0.0-beta` | Hash | General API limits, controlled bursts, smooth request processing. |
| **Sliding Window** | *Roadmap* | Sorted Set | High-precision enforcement, preventing burst windows. |
| **Fixed Window** | *Roadmap* | String (TTL) | Basic strict boundaries.* |

*\*Note on Fixed Window: It suffers from a known edge case where clients can exceed the limit by sending a burst of requests exactly at the boundary of a time window. Use Token Bucket for general use cases.*

---

## Quick Start

Add the starter dependency to your project. It is hosted on GitHub Packages.

**Maven:**
```xml
<dependency>
    <groupId>io.ratemaster</groupId>
    <artifactId>ratemaster-spring-boot-starter</artifactId>
    <version>1.0.0-beta</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.ratemaster:ratemaster-spring-boot-starter:1.0.0-beta'
```

### 1. Minimal Configuration (`application.yml`)
Provide standard Spring Data Redis properties and configure the RateMaster timeout:
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 400ms # Important: should be <= ratemaster.redis.command-timeout-ms

ratemaster:
  redis:
    command-timeout-ms: 500
```

### 2. Annotate your Endpoints
```java
import io.ratemaster.starter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    // Allows 10 requests per second, with a burst capacity of 50.
    // By default, the limit is applied per client IP address.
    @GetMapping("/api/data")
    @RateLimit(name = "data-endpoint", capacity = 50, refillRate = 10.0)
    public String getData() {
        return "Here is your data!";
    }
}
```

---

## Architecture Diagram

The following diagram illustrates how RateMaster isolates domain logic from infrastructure frameworks, adhering to Clean Architecture principles.

```mermaid
graph TD
    subgraph "Consumer Application"
        App[Spring Boot Controller] --> Aspect[@RateLimit Aspect]
        Aspect --> KeyResolver[RateLimitKeyResolver]
    end

    subgraph "RateMaster Spring Boot Starter"
        Aspect --> Adapter[SpringDataRedisScriptExecutor]
    end

    subgraph "RateMaster Core (Hexagonal)"
        Adapter -. implements .-> Port[LuaScriptExecutor]
        Port --> TokenBucket[TokenBucketRateLimiter]
    end

    subgraph "Infrastructure"
        TokenBucket -. Executes .-> Redis[(Redis Server)]
        Redis -. Atomic execution .-> Lua[token_bucket.lua]
    end

    classDef core fill:#4287f5,stroke:#fff,stroke-width:2px,color:#fff;
    class TokenBucket,Port core;
```

> **Visual Note:** The blue `RateMaster Core` components have **ZERO** dependencies on Spring Framework or specific Redis drivers. They rely purely on standard Java and define the `LuaScriptExecutor` port for framework-agnostic execution.

---

## KeyResolvers

RateMaster provides multiple strategies for identifying clients and grouping requests dynamically.

| Bean Name | Criteria | Example Use Case |
| :--- | :--- | :--- |
| `IpKeyResolver` *(Default)* | Client IP address | General public APIs |
| `HeaderKeyResolver` | Specific HTTP Header | B2B APIs (e.g., `X-API-Key`) |
| `PrincipalKeyResolver` | Authenticated User | Logged-in user routes |
| `SpELKeyResolver` | Spring Expression Language | Dynamic tenant/user limits based on payload |

### Implementing a Custom Key Resolver
You can easily create custom resolution logic by implementing the `RateLimitKeyResolver` SPI:

```java
import io.ratemaster.starter.resolver.RateLimitKeyResolver;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

@Component
public class TenantKeyResolver implements RateLimitKeyResolver {
    @Override
    public String resolveKey(MethodInvocation invocation) {
        // Extract tenant ID from context, headers, or method arguments
        return TenantContext.getCurrentTenantId();
    }
}
```

Then, reference it in your annotation:
```java
@RateLimit(name = "tenant-api", capacity = 100, refillRate = 5.0, keyResolver = TenantKeyResolver.class)
```

*(Note: RateMaster automatically sanitizes all resolved keys to prevent Redis key injection attacks).*

---

## Resilience & Fallback

RateMaster handles Redis unavailability gracefully without pulling heavy circuit breaker dependencies like Resilience4j. You can configure the behavior using the `fallback` attribute on the annotation.

| Strategy | Behavior | Best For |
| :--- | :--- | :--- |
| **OPEN** *(Fail-open)* | Allows the request if Redis is down/times out. | General non-critical APIs where availability is more important than strict limiting. |
| **CLOSED** *(Fail-closed)* | Rejects the request with HTTP 503 if Redis is down. | Brute-force protection, login endpoints, expensive billing endpoints. |

### Configuration Example
```java
@GetMapping("/api/billing")
@RateLimit(
    name = "billing-endpoint", 
    capacity = 5, 
    refillRate = 1.0, 
    fallback = RateLimitFallback.CLOSED // Will return 503 if Redis fails
)
public String processBilling() {
    return "Billing processed.";
}
```

---

## Metrics (Micrometer + Actuator)

RateMaster natively integrates with Micrometer to expose observability metrics. 
The following metrics are exported automatically:

- **`ratemaster.requests.allowed`**: Counter for requests that successfully passed the rate limit or were allowed via fail-open fallback.
- **`ratemaster.requests.rejected`**: Counter for requests that were rejected.

**Available Tags:**
- `limitName`: The name of the bucket defined in the annotation.
- `clientKey`: The sanitized key resolved for the client.
- `reason`: Populated only for rejected requests (e.g., `RATE_LIMIT` or `REDIS_FALLBACK_CLOSED`).

**Sample Actuator Query:**
```http
GET /actuator/metrics/ratemaster.requests.rejected
```

---

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

---

## Configuration Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `ratemaster.redis.command-timeout-ms` | int | 500 | Max time to wait for Redis response before triggering fallback. |
| `ratemaster.executor.coreSize` | int | - | Platform thread pool core size (if virtual threads disabled). |
| `ratemaster.executor.maxSize` | int | - | Platform thread pool max size. |

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

---

## Contributing

We welcome contributions! To get started:

1. Clone the repository.
2. Build and test the project locally. 
   *(Note: Requires Docker running for Testcontainers).*
   ```bash
   mvn clean verify
   ```
3. Submit a Pull Request.
