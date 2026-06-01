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
}

