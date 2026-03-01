package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link JwtAuthenticationFilter}.
 * 3 positive and 3 negative scenarios.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private Jwt jwt;

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain passThroughChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtDecoder);
        ReflectionTestUtils.setField(filter, "whitelistedPaths",
                List.of("/api/products/public/**", "/actuator/health"));
        passThroughChain = exchange -> Mono.empty();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void givenWhitelistedPath_whenFiltered_thenSkipsJwtValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        verifyNoInteractions(jwtDecoder);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // no 401 set
    }

    @Test
    void givenValidJwtWithRoles_whenFiltered_thenPropagatesClaimsAsHeaders() {
        when(jwt.getSubject()).thenReturn("user-42");
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("USER", "ADMIN"));
        when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build());

        // Capture the mutated exchange passed to the chain
        GatewayFilterChain capturingChain = ex -> {
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-ID")).isEqualTo("user-42");
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Roles")).contains("USER");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, capturingChain)).verifyComplete();
    }

    @Test
    void givenValidJwtWithNoRoles_whenFiltered_thenAddsEmptyRolesHeader() {
        when(jwt.getSubject()).thenReturn("user-99");
        when(jwt.getClaimAsStringList("roles")).thenReturn(null);
        when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtDecoder.decode("no-roles-token")).thenReturn(jwt);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer no-roles-token")
                        .build());

        GatewayFilterChain capturingChain = ex -> {
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Roles")).isEmpty();
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, capturingChain)).verifyComplete();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void givenMissingAuthorizationHeader_whenFiltered_thenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void givenMalformedAuthorizationHeader_whenFiltered_thenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .build());

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void givenInvalidJwt_whenFiltered_thenReturns401() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("Token expired"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOrder_returnsNegative100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}
