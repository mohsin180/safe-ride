package com.saferide.monolith;

import com.saferide.monolith.rides.config.PricingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SafeRide monolith — all former microservices (user, profile, rides,
 * messaging, notification) run in this single Spring Boot application on
 * port 8080, preserving the exact /api/v1/** paths the Flutter app uses.
 */
@SpringBootApplication
@EnableAsync
// Drives AbandonedSignupSweeper, which frees the email addresses of signups
// nobody finished.
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(PricingProperties.class)
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
