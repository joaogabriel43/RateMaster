package io.ratemaster.examples;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateMasterIntegrationIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldEnforceRateLimitAndReturn429WithRetryAfter() {
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        
        // Bucket capacity is 2, refill is 0.5/sec. 
        // 1. First request should succeed
        ResponseEntity<Map> response1 = restClient.get().uri("/api/hello").retrieve().toEntity(Map.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).containsEntry("message", "Hello, RateMaster!");

        // 2. Second request should succeed
        ResponseEntity<Map> response2 = restClient.get().uri("/api/hello").retrieve().toEntity(Map.class);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Third request should fail with 429 Too Many Requests
        HttpClientErrorException ex = catchThrowableOfType(
                () -> restClient.get().uri("/api/hello").retrieve().toEntity(Map.class),
                HttpClientErrorException.class
        );
        
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        // 4. Validate Retry-After header
        String retryAfterStr = ex.getResponseHeaders().getFirst("Retry-After");
        assertThat(retryAfterStr).isNotNull();
        int retryAfterSeconds = Integer.parseInt(retryAfterStr);
        assertThat(retryAfterSeconds).isGreaterThanOrEqualTo(1);

        // 5. Wait for the retry-after duration
        await().atMost(retryAfterSeconds + 2, SECONDS).pollDelay(retryAfterSeconds, SECONDS)
                .untilAsserted(() -> {
                    ResponseEntity<Map> retryResponse = restClient.get().uri("/api/hello").retrieve().toEntity(Map.class);
                    assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                });
    }
    @Test
    void shouldHandleRedisTimeoutsWithConfiguredFallbacks() throws Exception {
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        
        // Pause Redis to simulate a hard timeout
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        
        try {
            // Test A & C: Fallback OPEN returns 200
            ResponseEntity<Map> responseOpen = restClient.get().uri("/api/fallback-open").retrieve().toEntity(Map.class);
            assertThat(responseOpen.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(responseOpen.getBody()).containsEntry("message", "open");
            
            // Test B & C: Fallback CLOSED returns 503
            org.springframework.web.client.HttpServerErrorException ex = catchThrowableOfType(
                    () -> restClient.get().uri("/api/fallback-closed").retrieve().toEntity(Map.class),
                    org.springframework.web.client.HttpServerErrorException.class
            );
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            
        } finally {
            // Unpause Redis to restore health for other potential tests or teardown
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        // Test D: Verify Actuator Metrics
        await().atMost(5, SECONDS).untilAsserted(() -> {
            ResponseEntity<Map> metricsAllowed = catchThrowableOfType(
                    () -> restClient.get().uri("/actuator/metrics/ratemaster.requests.allowed").retrieve().toEntity(Map.class),
                    HttpClientErrorException.class) == null 
                    ? restClient.get().uri("/actuator/metrics/ratemaster.requests.allowed").retrieve().toEntity(Map.class)
                    : null;
                    
            ResponseEntity<Map> metricsRejected = catchThrowableOfType(
                    () -> restClient.get().uri("/actuator/metrics/ratemaster.requests.rejected").retrieve().toEntity(Map.class),
                    HttpClientErrorException.class) == null
                    ? restClient.get().uri("/actuator/metrics/ratemaster.requests.rejected").retrieve().toEntity(Map.class)
                    : null;

            assertThat(metricsAllowed).isNotNull();
            assertThat(metricsRejected).isNotNull();
            assertThat(metricsAllowed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(metricsRejected.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            Counter rejectedCounter = meterRegistry.find("ratemaster.requests.rejected")
                .tag("reason", "REDIS_FALLBACK_CLOSED")
                .counter();
            assertThat(rejectedCounter).isNotNull();
            assertThat(rejectedCounter.count()).isGreaterThan(0);
        });
    }

    @Test
    void shouldSanitizeMaliciousKey() {
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        
        // Use HeaderKeyResolver (assuming demo endpoint uses a custom header, or we can just pass a malicious header if we have an endpoint)
        // Wait, DemoController's /api/hello uses IpKeyResolver. We can send a malicious X-Forwarded-For header.
        ResponseEntity<Map> response = restClient.get()
                .uri("/api/hello")
                .header("X-Forwarded-For", "1.2.3.4:ratemaster:tokenbucket:other:user")
                .retrieve()
                .toEntity(Map.class);
                
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Assert that the metric is recorded with the sanitized key!
        // The original is "1.2.3.4:ratemaster:tokenbucket:other:user"
        // Sanitized should be "1.2.3.4-ratemaster-tokenbucket-other-user"
        Counter allowedCounter = meterRegistry.find("ratemaster.requests.allowed")
                .tag("clientKey", "1.2.3.4-ratemaster-tokenbucket-other-user")
                .counter();
                
        assertThat(allowedCounter).isNotNull();
        assertThat(allowedCounter.count()).isGreaterThan(0);
    }
}
