# API Gateway -- Implementation

> **Stack:** Spring Boot 4.0.0, Spring Cloud Gateway, Spring Security (JWT), Resilience4j, Redis, Java 17
> **Architecture:** Reactive (non-blocking) gateway with declarative and programmatic route configuration

---

## Table of Contents

1. [Route Configuration (YAML)](#1-route-configuration-yaml)
2. [JWT Authentication Filter](#2-jwt-authentication-filter)
3. [Correlation ID Filter](#3-correlation-id-filter)
4. [Rate Limiter Configuration](#4-rate-limiter-configuration)
5. [Circuit Breaker with Fallback](#5-circuit-breaker-with-fallback)
6. [Fallback Controller](#6-fallback-controller)
7. [Security Configuration](#7-security-configuration)
8. [Programmatic Route Configuration (Alternative)](#8-programmatic-route-configuration-alternative)
9. [Custom Request/Response Logging Filter](#9-custom-requestresponse-logging-filter)
10. [Application Properties](#10-application-properties)

---

## 1. Route Configuration (YAML)

The gateway's route configuration maps incoming request paths to backend services. Each route can have its own set of filters.

```yaml
# application.yml -- Gateway routes

spring:
  cloud:
    gateway:
      routes:
        # --- Product Service ---
        - id: product-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/products/**
          filters:
            - name: CircuitBreaker
              args:
                name: productServiceCB
                fallbackUri: forward:/fallback/products
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10  # 10 requests per second steady state
                redis-rate-limiter.burstCapacity: 20   # Allow bursts up to 20
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"
            - RewritePath=/api/products/(?<segment>.*), /api/products/${segment}

        # --- User Service ---
        - id: user-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/users/**
          filters:
            - name: CircuitBreaker
              args:
                name: userServiceCB
                fallbackUri: forward:/fallback/users
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 20
                redis-rate-limiter.burstCapacity: 40
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"

        # --- Order Service ---
        - id: order-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/orders
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5   # Stricter for writes
                redis-rate-limiter.burstCapacity: 10
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"

        # --- Review Service ---
        - id: review-service
          uri: http://localhost:8084
          predicates:
            - Path=/api/reviews/**
          filters:
            - name: CircuitBreaker
              args:
                name: reviewServiceCB
                fallbackUri: forward:/fallback/reviews
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 15
                redis-rate-limiter.burstCapacity: 30
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"

      # Global default filters applied to ALL routes
      default-filters:
        - AddRequestHeader=X-Gateway-Timestamp, ${spring.cloud.gateway.timestamp:#{T(java.time.Instant).now().toString()}}
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST
```

---

## 2. JWT Authentication Filter

A global filter that validates JWT tokens on every request (except whitelisted paths) and propagates user claims to downstream services.

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global gateway filter that validates JWT Bearer tokens.
 * <p>
 * For every incoming request (except whitelisted paths), this filter:
 * <ol>
 *   <li>Extracts the Bearer token from the Authorization header.</li>
 *   <li>Validates the token signature, expiry, and issuer using Spring Security's
 *       {@link JwtDecoder}.</li>
 *   <li>Extracts claims (user ID, roles) and adds them as headers for
 *       downstream services.</li>
 *   <li>Rejects invalid/missing tokens with HTTP 401.</li>
 * </ol>
 * </p>
 *
 * <p>This filter runs at order {@code -100} to ensure it executes before
 * any route-specific filters (rate limiter, circuit breaker).</p>
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtDecoder jwtDecoder;

    /** Paths that do not require authentication (e.g., health checks, public catalog) */
    @Value("${gateway.auth.whitelist:/api/products/public/**,/actuator/health}")
    private List<String> whitelistedPaths;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for whitelisted paths
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // Validate the JWT
            Jwt jwt = jwtDecoder.decode(token);

            // Extract claims and propagate as headers to downstream services
            String userId = jwt.getSubject();
            String roles = String.join(",",
                    jwt.getClaimAsStringList("roles") != null
                            ? jwt.getClaimAsStringList("roles")
                            : List.of());

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-ID", userId)
                    .header("X-User-Roles", roles)
                    .header("X-Auth-Token-Expiry", jwt.getExpiresAt().toString())
                    .build();

            log.debug("JWT validated for user {} with roles [{}]", userId, roles);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;  // Run before rate limiter and circuit breaker
    }

    private boolean isWhitelisted(String path) {
        return whitelistedPaths.stream().anyMatch(pattern -> {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            return path.matches(regex);
        });
    }
}
```

---

## 3. Correlation ID Filter

Generates a unique correlation ID for every request, enabling distributed tracing across all services.

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures every request has a unique correlation ID.
 * <p>
 * If the incoming request already has an {@code X-Correlation-ID} header
 * (e.g., from an upstream load balancer), it is preserved. Otherwise, a
 * new UUID is generated.
 * </p>
 * <p>
 * The correlation ID is:
 * <ul>
 *   <li>Added to the request headers (forwarded to downstream services)</li>
 *   <li>Added to the response headers (returned to the client)</li>
 *   <li>Logged with every gateway log entry for traceability</li>
 * </ul>
 * </p>
 */
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Use existing correlation ID or generate a new one
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("[{}] {} {} -> routing",
                correlationId,
                request.getMethod(),
                request.getURI().getPath());

        // Add to request (forwarded to downstream)
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Add to response (returned to client)
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200;  // Run first, before JWT filter
    }
}
```

---

## 4. Rate Limiter Configuration

Uses Redis to enforce per-user rate limits across multiple gateway instances.

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiter key resolver configuration.
 * <p>
 * Determines how rate limits are applied. By default, limits are per-user
 * (based on the X-User-ID header set by the JWT filter). For unauthenticated
 * routes, falls back to the client IP address.
 * </p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate limit key to the authenticated user's ID.
     * If no user ID is present (unauthenticated route), uses the client IP.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback to IP address for unauthenticated routes
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Alternative: per-API-path rate limiting (useful for public APIs).
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getURI().getPath());
    }
}
```

---

## 5. Circuit Breaker with Fallback

Resilience4j circuit breaker configuration for each backend service.

```java
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
```

---

## 6. Fallback Controller

Provides graceful degradation when a circuit breaker trips.

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller invoked when a circuit breaker is open.
 * <p>
 * Returns service-specific degraded responses so the client can render
 * a partial UI rather than showing an error page. For example, the product
 * page can still show product details even if reviews are unavailable.
 * </p>
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> productsFallback() {
        log.warn("Product Service circuit breaker OPEN -- returning fallback");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "product-service",
                        "status", "unavailable",
                        "message", "Product service is temporarily unavailable. Please retry shortly.",
                        "timestamp", Instant.now().toString(),
                        "data", Map.of()
                ));
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> usersFallback() {
        log.warn("User Service circuit breaker OPEN -- returning fallback");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "user-service",
                        "status", "unavailable",
                        "message", "User service is temporarily unavailable.",
                        "timestamp", Instant.now().toString()
                ));
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        log.warn("Order Service circuit breaker OPEN -- returning fallback");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "order-service",
                        "status", "unavailable",
                        "message", "Order service is temporarily unavailable. Your order may still be processing.",
                        "timestamp", Instant.now().toString()
                ));
    }

    @GetMapping("/reviews")
    public ResponseEntity<Map<String, Object>> reviewsFallback() {
        log.warn("Review Service circuit breaker OPEN -- returning fallback");
        return ResponseEntity.ok(Map.of(
                "service", "review-service",
                "status", "degraded",
                "message", "Reviews are temporarily unavailable.",
                "reviews", java.util.List.of(),
                "timestamp", Instant.now().toString()
        ));
    }
}
```

---

## 7. Security Configuration

Configures Spring Security for reactive (WebFlux) context with JWT validation.

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration for the API Gateway.
 * <p>
 * Uses Spring Security's reactive WebFlux security with OAuth2 resource
 * server (JWT) configuration. The actual token validation is delegated
 * to the {@link JwtAuthenticationFilter} global filter for fine-grained
 * control over whitelisting and header propagation.
 * </p>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)  // APIs use tokens, not cookies
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        .pathMatchers("/api/products/public/**").permitAll()
                        .anyExchange().permitAll()  // JWT validation is handled by our custom filter
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://retailhub.com",
                "https://admin.retailhub.com",
                "http://localhost:3000"  // Local development
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

---

## 8. Programmatic Route Configuration (Alternative)

For teams that prefer Java code over YAML for route definitions.

```java
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
```

---

## 9. Custom Request/Response Logging Filter

```java
package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs request and response metadata for observability.
 * <p>
 * Logs include the correlation ID, HTTP method, path, response status,
 * and elapsed time. These structured logs integrate with ELK, Datadog,
 * or any log aggregation platform.
 * </p>
 */
@Component
@Slf4j
public class RequestResponseLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String correlationId = request.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatusCode() != null
                    ? response.getStatusCode().value()
                    : 0;

            log.info("[{}] {} {} -> {} ({}ms)",
                    correlationId, method, path, status, duration);
        }));
    }

    @Override
    public int getOrder() {
        return -150;  // After correlation ID, before JWT
    }
}
```

---

## 10. Application Properties

```yaml
# application.yml -- API Gateway configuration

spring:
  application:
    name: api-gateway

  # --- Spring Cloud Gateway ---
  cloud:
    gateway:
      # Global CORS can also be configured here
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://retailhub.com,http://localhost:3000"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true

  # --- Redis for Rate Limiting ---
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

  # --- Security: JWT validation ---
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://auth.retailhub.com/.well-known/jwks.json
          # For local development with a self-signed key:
          # jwk-set-uri: http://localhost:8090/oauth2/jwks

# --- Gateway Auth Configuration ---
gateway:
  auth:
    whitelist:
      - /api/products/public/**
      - /actuator/health
      - /actuator/info

# --- Resilience4j Circuit Breaker Monitoring ---
resilience4j:
  circuitbreaker:
    instances:
      productServiceCB:
        registerHealthIndicator: true
      userServiceCB:
        registerHealthIndicator: true
      orderServiceCB:
        registerHealthIndicator: true
      reviewServiceCB:
        registerHealthIndicator: true

# --- Actuator Endpoints ---
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway,circuitbreakers,circuitbreakerevents,ratelimiters
  endpoint:
    gateway:
      enabled: true
    health:
      show-details: always

server:
  port: 8080

logging:
  level:
    com.saurabhshcs.adtech.microservices.designpattern.gateway: DEBUG
    org.springframework.cloud.gateway: DEBUG
    io.github.resilience4j: DEBUG
```

---

## Example Interactions

### Successful Authenticated Request

```http
GET /api/products?q=laptop HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Gateway forwards to Product Service with added headers:**

```http
GET /api/products?q=laptop HTTP/1.1
Host: product-service:8081
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
X-User-ID: user-123
X-User-Roles: ROLE_CUSTOMER,ROLE_PREMIUM
X-Correlation-ID: 7f3a9b2c-4d5e-6f7a-8b9c-0d1e2f3a4b5c
X-Auth-Token-Expiry: 2026-02-25T15:00:00Z
X-Gateway-Timestamp: 2026-02-25T14:30:00Z
```

### Rate Limited Response

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-RateLimit-Remaining: 0
X-RateLimit-Replenish-Rate: 10
X-RateLimit-Burst-Capacity: 20
Retry-After: 1
X-Correlation-ID: 7f3a9b2c-4d5e-6f7a-8b9c-0d1e2f3a4b5c
```

### Circuit Breaker Fallback Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Correlation-ID: 7f3a9b2c-4d5e-6f7a-8b9c-0d1e2f3a4b5c

{
  "service": "review-service",
  "status": "degraded",
  "message": "Reviews are temporarily unavailable.",
  "reviews": [],
  "timestamp": "2026-02-25T14:31:00Z"
}
```

---

*Next: [dependencies.md](./dependencies.md) -- full build file and infrastructure setup.*
