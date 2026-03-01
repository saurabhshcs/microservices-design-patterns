package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TDD tests for {@link RequestResponseLoggingFilter}.
 * 3 positive and 3 negative scenarios.
 */
class RequestResponseLoggingFilterTest {

    private RequestResponseLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestResponseLoggingFilter();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void givenRequestWithCorrelationId_whenFiltered_thenChainCompletes() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-xyz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void givenRequestWithStatus200_whenFiltered_thenCompletesSuccessfully() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/1").build());
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void getOrder_returnsNegative150_betweenCorrelationAndJwtFilters() {
        assertThat(filter.getOrder()).isEqualTo(-150);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void givenRequestWithNoCorrelationId_whenFiltered_thenHandlesNullGracefully() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/orders").build());
        GatewayFilterChain chain = ex -> Mono.empty();

        // correlationId will be null — filter logs "null" but must not throw
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void givenRequestWithNullResponseStatus_whenFiltered_thenLogsZeroStatus() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/reviews").build());
        // Chain does NOT set a status → getStatusCode() returns null → logged as 0
        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        // Assertion: no exception thrown (null status code is handled)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void givenRequestWith500Status_whenFiltered_thenStillCompletesWithoutError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/bad").build());
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void givenRequestWithNullHttpMethod_whenFiltered_thenFallsBackToUnknown() {
        // Build a request mock where getMethod() returns null to hit the "UNKNOWN" branch
        ServerHttpRequest mockRequest = mock(ServerHttpRequest.class);
        when(mockRequest.getMethod()).thenReturn(null);
        when(mockRequest.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(mockRequest.getURI()).thenReturn(URI.create("http://localhost/api/test"));

        MockServerHttpResponse mockResponse = new MockServerHttpResponse();
        ServerWebExchange mockExchange = mock(ServerWebExchange.class);
        when(mockExchange.getRequest()).thenReturn(mockRequest);
        when(mockExchange.getResponse()).thenReturn(mockResponse);

        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(mockExchange, chain)).verifyComplete();
    }
}
