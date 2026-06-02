RateMaster v1.0.0-beta

First public release of the Token Bucket distributed rate limiter.

What's included

✅ Token Bucket algorithm with lazy refill via Lua atomic script

✅ @RateLimit annotation with Spring AOP interception

✅ KeyResolver SPI: IP, Header, Principal (JWT), SpEL

✅ Fail-open / fail-closed fallback with configurable timeout

✅ Virtual Threads support (Java 21) with platform thread pool fallback

✅ Micrometer metrics (ratemaster.requests.allowed / rejected)

✅ Redis key sanitization (prevents injection attacks)

✅ Spring Boot 4.0.x / Spring Framework 7 compatible

What's next (roadmap)

🚧 Sliding Window algorithm

🚧 Fixed Window algorithm

🚧 Angular metrics dashboard

Requirements

Java 21+, Spring Boot 4.0.x, Redis 7+
