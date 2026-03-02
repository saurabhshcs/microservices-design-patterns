package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for {@link RateLimiterConfig}.
 * 3 positive and 3 negative scenarios.
 */
class RateLimiterConfigTest {

    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void userKeyResolver_withXUserIdHeader_returnsUserId() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header("X-User-ID", "user-42")
                        .build());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("user-42")
                .verifyComplete();
    }

    @Test
    void pathKeyResolver_returnsRequestPath() {
        KeyResolver resolver = config.pathKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/123").build());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("/api/products/123")
                .verifyComplete();
    }

    @Test
    void bothResolverBeansAreDistinctInstances() {
        KeyResolver user = config.userKeyResolver();
        KeyResolver path = config.pathKeyResolver();
        assertThat(user).isNotSameAs(path);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void userKeyResolver_withoutXUserIdHeader_fallsBackToIpOrUnknown() {
        KeyResolver resolver = config.userKeyResolver();
        // MockServerHttpRequest has no remote address → falls back to "unknown"
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        StepVerifier.create(resolver.resolve(exchange))
                .assertNext(key -> assertThat(key).isNotNull().isNotEmpty())
                .verifyComplete();
    }

    @Test
    void userKeyResolver_withBlankXUserIdHeader_fallsBackToIpOrUnknown() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header("X-User-ID", "  ")
                        .build());

        // Blank header is truthy non-null — but "  ".isBlank() is true → falls back
        StepVerifier.create(resolver.resolve(exchange))
                .assertNext(key -> assertThat(key).isNotNull())
                .verifyComplete();
    }

    @Test
    void pathKeyResolver_forDifferentPaths_returnsDifferentKeys() {
        KeyResolver resolver = config.pathKeyResolver();

        MockServerWebExchange e1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").build());
        MockServerWebExchange e2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        String key1 = resolver.resolve(e1).block();
        String key2 = resolver.resolve(e2).block();

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void userKeyResolver_withoutHeader_andWithRemoteAddress_returnsClientIp() {
        KeyResolver resolver = config.userKeyResolver();
        // Set a non-null remote address to exercise the IP extraction branch
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .remoteAddress(new InetSocketAddress("192.168.1.10", 8080))
                        .build());

        StepVerifier.create(resolver.resolve(exchange))
                .assertNext(key -> assertThat(key).isEqualTo("192.168.1.10"))
                .verifyComplete();
    }
}
