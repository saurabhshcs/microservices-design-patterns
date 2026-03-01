package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker configuration for gateway routes.
 * <p>
 * Each backend service gets its own circuit breaker instance with tunable
 * thresholds. The configuration follows the principle of least surprise:
 * <ul>
 *   <li>Failure rate threshold: 50% (open circuit if half the requests fail)</li>
 *   <li>Sliding window: 10 calls (enough to detect sustained failures,
 *       not so small that a single error opens the circuit)</li>
 *   <li>Wait duration in open state: 30 seconds (give the service time to recover)</li>
 *   <li>Time limiter: 3 seconds (prevent hanging requests)</li>
 * </ul>
 * </p>
 */
@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build())
                .build());
    }

    /**
     * Custom configuration for the Order Service -- stricter thresholds
     * because payment-related failures are more critical.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> orderServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .failureRateThreshold(30)      // More sensitive
                                .slidingWindowSize(5)
                                .waitDurationInOpenState(Duration.ofSeconds(60))  // Longer recovery window
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(5))  // More time for order operations
                                .build()),
                "orderServiceCB");
    }
}
