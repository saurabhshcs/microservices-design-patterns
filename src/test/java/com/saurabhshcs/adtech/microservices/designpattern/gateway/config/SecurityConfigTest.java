package com.saurabhshcs.adtech.microservices.designpattern.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link SecurityConfig}.
 * Tests jwtDecoder() and corsConfigurationSource() directly without Spring context.
 * The securityWebFilterChain() is covered by the ProgrammaticRouteConfigTest integration test.
 * 3 positive and 3 negative scenarios.
 */
class SecurityConfigTest {

    private SecurityConfig config;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwkSetUri", "https://example.com/.well-known/jwks.json");
    }

    private CorsConfiguration corsConfig() {
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any-path").build());
        return source.getCorsConfiguration(exchange);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void jwtDecoder_isCreated_andIsNonNull() {
        JwtDecoder decoder = config.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void corsConfigurationSource_allowsKnownOrigins() {
        assertThat(corsConfig()).isNotNull();
        assertThat(corsConfig().getAllowedOrigins()).contains("https://retailhub.com");
    }

    @Test
    void corsConfigurationSource_includesAllHttpMethods() {
        assertThat(corsConfig().getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void corsConfigurationSource_doesNotAllowUnknownOrigins() {
        assertThat(corsConfig().getAllowedOrigins()).doesNotContain("https://malicious.example.com");
    }

    @Test
    void corsConfigurationSource_allowsCredentials() {
        assertThat(corsConfig().getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfigurationSource_maxAgeIs3600Seconds() {
        assertThat(corsConfig().getMaxAge()).isEqualTo(3600L);
    }
}
