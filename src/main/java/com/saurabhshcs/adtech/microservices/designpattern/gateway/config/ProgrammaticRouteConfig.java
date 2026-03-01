package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Programmatic route configuration -- alternative to YAML.
 * <p>
 * Activated with the {@code programmatic-routes} profile. Useful when
 * route logic requires conditional routing, dynamic weights, or integration
 * with external configuration sources (e.g., feature flags).
 * </p>
 *
 * <p>To use: {@code spring.profiles.active=programmatic-routes}</p>
 */
@Configuration
@Profile("programmatic-routes")
public class ProgrammaticRouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                // Product Service: read-heavy, generous rate limits
                .route("product-service", r -> r
                        .path("/api/products/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("productServiceCB")
                                        .setFallbackUri("forward:/fallback/products"))
                                .addRequestHeader("X-Routed-By", "gateway")
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setStatuses(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("http://localhost:8081"))

                // User Service
                .route("user-service", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("userServiceCB")
                                        .setFallbackUri("forward:/fallback/users")))
                        .uri("http://localhost:8082"))

                // Order Service: stricter handling for financial operations
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("orderServiceCB")
                                        .setFallbackUri("forward:/fallback/orders"))
                                .addRequestHeader("X-Gateway-Route", "order-service"))
                        .uri("http://localhost:8083"))

                // Review Service
                .route("review-service", r -> r
                        .path("/api/reviews/**")
                        .filters(f -> f
                                .circuitBreaker(config -> config
                                        .setName("reviewServiceCB")
                                        .setFallbackUri("forward:/fallback/reviews")))
                        .uri("http://localhost:8084"))

                .build();
    }
}
