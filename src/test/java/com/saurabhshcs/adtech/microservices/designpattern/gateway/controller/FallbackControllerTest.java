package com.saurabhshcs.adtech.microservices.designpattern.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * TDD tests for {@link FallbackController}.
 * 3 positive and 3 negative scenarios.
 */
class FallbackControllerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new FallbackController()).build();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void productsFallback_returns503WithServiceUnavailableBody() {
        webTestClient.get().uri("/fallback/products")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("product-service")
                .jsonPath("$.status").isEqualTo("unavailable");
    }

    @Test
    void reviewsFallback_returns200WithDegradedBody() {
        // Reviews are non-critical — degraded mode returns 200 not 503
        webTestClient.get().uri("/fallback/reviews")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("degraded")
                .jsonPath("$.reviews").isArray();
    }

    @Test
    void ordersFallback_returns503WithOrderServiceInfo() {
        webTestClient.get().uri("/fallback/orders")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("order-service")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void usersFallback_returns503NotOk() {
        webTestClient.get().uri("/fallback/users")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("user-service");
    }

    @Test
    void productsFallback_doesNotReturnEmptyData() {
        webTestClient.get().uri("/fallback/products")
                .exchange()
                .expectBody()
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void reviewsFallback_returnsEmptyReviewsList_notNull() {
        webTestClient.get().uri("/fallback/reviews")
                .exchange()
                .expectBody()
                .jsonPath("$.reviews").isArray()
                .jsonPath("$.service").isEqualTo("review-service");
    }
}
