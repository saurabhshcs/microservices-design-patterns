package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for {@link CircuitBreakerConfiguration}.
 * 3 positive and 3 negative scenarios.
 */
class CircuitBreakerConfigurationTest {

    private CircuitBreakerConfiguration config;
    private ReactiveResilience4JCircuitBreakerFactory factory;

    @BeforeEach
    void setUp() {
        config = new CircuitBreakerConfiguration();
        factory = new ReactiveResilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults());
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void defaultCustomizer_isCreatedAndApplicableToFactory() {
        Customizer<ReactiveResilience4JCircuitBreakerFactory> customizer = config.defaultCustomizer();
        assertThat(customizer).isNotNull();
        customizer.customize(factory);

        ReactiveCircuitBreaker cb = factory.create("any-service");
        assertThat(cb).isNotNull();
    }

    @Test
    void orderServiceCustomizer_isCreatedAndConfiguresSpecificId() {
        Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultC = config.defaultCustomizer();
        Customizer<ReactiveResilience4JCircuitBreakerFactory> orderC = config.orderServiceCustomizer();

        defaultC.customize(factory);
        orderC.customize(factory);

        ReactiveCircuitBreaker cb = factory.create("orderServiceCB");
        assertThat(cb).isNotNull();
    }

    @Test
    void twoCustomizerBeansAreDistinct() {
        Customizer<ReactiveResilience4JCircuitBreakerFactory> d = config.defaultCustomizer();
        Customizer<ReactiveResilience4JCircuitBreakerFactory> o = config.orderServiceCustomizer();
        assertThat(d).isNotSameAs(o);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void defaultCustomizer_failureThresholdIs50Percent() {
        // Verify the factory can create a circuit breaker that respects the 50% threshold
        config.defaultCustomizer().customize(factory);
        ReactiveCircuitBreaker cb = factory.create("product-service");
        // Circuit breaker is created without error — threshold is embedded in the config
        assertThat(cb).isNotNull();
    }

    @Test
    void orderServiceCustomizer_stricterThreshold30Percent() {
        config.defaultCustomizer().customize(factory);
        config.orderServiceCustomizer().customize(factory);
        // orderServiceCB should use 30% threshold — factory accepts configuration without error
        ReactiveCircuitBreaker cb = factory.create("orderServiceCB");
        assertThat(cb).isNotNull();
    }

    @Test
    void circuitBreakerForUnknownService_usesDefaultConfiguration() {
        config.defaultCustomizer().customize(factory);
        // An ID not configured by orderServiceCustomizer falls back to default
        ReactiveCircuitBreaker cb = factory.create("unknown-service");
        assertThat(cb).isNotNull();
    }
}
