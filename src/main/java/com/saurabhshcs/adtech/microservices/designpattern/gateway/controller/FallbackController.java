package com.saurabhshcs.adtech.microservices.designpattern.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
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
                "reviews", List.of(),
                "timestamp", Instant.now().toString()
        ));
    }
}
