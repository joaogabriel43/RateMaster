package io.ratemaster.examples.controller;

import io.ratemaster.starter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {

    @GetMapping("/api/hello")
    @RateLimit(name = "demoApi", capacity = 2, refillRate = 0.5)
    public Map<String, String> hello() {
        return Map.of("message", "Hello, RateMaster!");
    }

    @GetMapping("/api/fallback-open")
    @RateLimit(name = "fallbackOpenApi", capacity = 10, refillRate = 10, fallback = io.ratemaster.starter.annotation.RateLimitFallback.OPEN)
    public Map<String, String> fallbackOpen() {
        return Map.of("message", "open");
    }

    @GetMapping("/api/fallback-closed")
    @RateLimit(name = "fallbackClosedApi", capacity = 10, refillRate = 10, fallback = io.ratemaster.starter.annotation.RateLimitFallback.CLOSED)
    public Map<String, String> fallbackClosed() {
        return Map.of("message", "closed");
    }
}
