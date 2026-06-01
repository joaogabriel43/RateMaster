package io.ratemaster.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo application for showcasing RateMaster rate limiting capabilities.
 *
 * <p>This application serves as a local development and integration testing
 * environment for the RateMaster library. It provides sample endpoints
 * that demonstrate rate limiting configurations and behaviors.</p>
 *
 * @since 0.1.0
 */
@SpringBootApplication
public class RateMasterExampleApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RateMasterExampleApplication.class, args);
    }
}
