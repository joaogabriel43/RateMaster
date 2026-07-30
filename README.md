# RateMaster

[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-18-red.svg)](https://angular.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub Packages](https://img.shields.io/badge/GitHub-Packages-blueviolet)](https://github.com/joaogabriel43/RateMaster/packages)

High-performance, distributed rate-limiting library for Spring Boot, built on Redis. RateMaster provides lightweight, resilient rate-limiting algorithms with atomic Lua script execution, guaranteeing zero race conditions across distributed microservices—vastly superior to in-memory locks. Seamlessly declarative via Spring AOP annotations.

**RateMaster now includes an L1 Cache Penalty Box and a Premium Angular 18 Dashboard!**

---

## Algorithms Status

RateMaster supports three distinct distributed algorithms out of the box, all executed atomically in Redis.

| Algorithm | Status | Redis Structure | Complexity | Best For |
| :--- | :--- | :--- | :--- | :--- |
| **Token Bucket** | `v1.0.0` | Hash | $O(1)$ | General API limits, controlled bursts, smooth request processing. |
| **Sliding Window** | `v1.0.0` | Sorted Set | $O(\log N)$ | High-precision enforcement, preventing burst windows. |
| **Fixed Window** | `v1.0.0` | String (TTL)| $O(1)$ | Basic strict boundaries, absolute clock limits. |

*Check out our [ADR-007](docs/adrs/ADR-007-algorithm-tradeoffs.md) for an in-depth architectural comparison of the trade-offs.*

---

## ⚡ L1 Cache Penalty Box & HTTP Headers

RateMaster incorporates a two-level rate limiting architecture:
- **L1 Cache (Caffeine)**: Temporarily blocks malicious clients locally to prevent them from overwhelming the Redis network with denied requests (Fast-fail Penalty Box).
- **L2 (Redis)**: The source of truth for distributed quotas.

Furthermore, RateMaster natively intercepts the Spring `RequestContextHolder` to inject standard IETF Rate-Limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`) into your HTTP responses out-of-the-box.

---

## Quick Start

Add the starter dependency to your project. It is hosted on GitHub Packages.

**Maven:**
```xml
<dependency>
    <groupId>io.ratemaster</groupId>
    <artifactId>ratemaster-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 1. Minimal Configuration (`application.yml`)
Provide standard Spring Data Redis properties and configure the RateMaster timeout and L1 cache:
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 400ms

ratemaster:
  redis:
    command-timeout-ms: 500
  local-cache:
    enabled: true
    max-size: 10000
```

### 2. Annotate your Endpoints
```java
import io.ratemaster.starter.annotation.RateLimit;
import io.ratemaster.starter.annotation.RateLimitAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    // Allows 10 requests per second, with a burst capacity of 50.
    @GetMapping("/api/data")
    @RateLimit(
        name = "data-endpoint", 
        algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
        capacity = 50, 
        refillRate = 10.0
    )
    public String getData() {
        return "Here is your data!";
    }
}
```

---

## 📊 RateMaster Dashboard

RateMaster now features a stunning visual dashboard built with **Angular 18**.
Navigate to `ratemaster-dashboard` to run the premium UI showcasing real-time metrics, traffic charts, and top blocked clients via a custom glassmorphic Design System.

```bash
cd ratemaster-dashboard
npm install
npm start
```

---

## Architecture Diagram

The following diagram illustrates how RateMaster isolates domain logic from infrastructure frameworks, adhering to Clean Architecture principles.

```mermaid
graph TD
    subgraph "Consumer Application"
        App[Spring Boot Controller] --> Aspect[@RateLimit Aspect]
        Aspect --> Cache[LocalPenaltyBox L1 Cache]
        Cache --> KeyResolver[RateLimitKeyResolver]
    end

    subgraph "RateMaster Spring Boot Starter"
        KeyResolver --> Adapter[SpringDataRedisScriptExecutor]
    end

    subgraph "RateMaster Core (Hexagonal)"
        Adapter -. implements .-> Port[LuaScriptExecutor]
        Port --> Limiter[RateLimiter Algorithms]
    end

    subgraph "Infrastructure"
        Limiter -. Executes .-> Redis[(Redis Server)]
    end

    classDef core fill:#4287f5,stroke:#fff,stroke-width:2px,color:#fff;
    class Limiter,Port core;
```

---

## KeyResolvers

RateMaster provides multiple strategies for identifying clients and grouping requests dynamically.

| Bean Name | Criteria | Example Use Case |
| :--- | :--- | :--- |
| `IpKeyResolver` *(Default)* | Client IP address | General public APIs |
| `HeaderKeyResolver` | Specific HTTP Header | B2B APIs (e.g., `X-API-Key`) |
| `PrincipalKeyResolver` | Authenticated User | Logged-in user routes |
| `SpELKeyResolver` | Spring Expression Language | Dynamic tenant/user limits |

*(Note: RateMaster automatically sanitizes all resolved keys to prevent Redis key injection attacks).*

---

## Resilience & Fallback

RateMaster handles Redis unavailability gracefully without pulling heavy circuit breaker dependencies like Resilience4j. You can configure the behavior using the `fallback` attribute on the annotation.

| Strategy | Behavior | Best For |
| :--- | :--- | :--- |
| **OPEN** *(Fail-open)* | Allows the request if Redis is down/times out. | General non-critical APIs. |
| **CLOSED** *(Fail-closed)* | Rejects the request with HTTP 503 if Redis is down. | Brute-force protection, billing endpoints. |

---

## Metrics (Micrometer + Actuator)

RateMaster natively integrates with Micrometer to expose observability metrics. 
The following metrics are exported automatically:

- **`ratemaster.requests.allowed`**: Counter for requests that successfully passed the rate limit.
- **`ratemaster.requests.rejected`**: Counter for requests that were rejected.
- **`ratemaster.l1.cache.blocks`**: Counter for requests blocked locally by the L1 Penalty Box.

**Available Tags:**
- `limitName`: The name of the bucket defined in the annotation.
- `clientKey`: The sanitized key resolved for the client.
- `reason`: Populated only for rejected requests (e.g., `RATE_LIMIT`, `L1_CACHE`, or `REDIS_FALLBACK_CLOSED`).

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
