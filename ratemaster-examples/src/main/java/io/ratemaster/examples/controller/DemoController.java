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
}
