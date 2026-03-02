package com.saurabhshcs.adtech.microservices.designpattern.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for {@link CorrelationIdFilter}.
 * 3 positive and 3 negative scenarios.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private GatewayFilterChain passThroughChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        passThroughChain = exchange -> Mono.empty();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void givenExistingCorrelationId_whenFiltered_thenPreservesIt() {
        String existingId = "trace-abc-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(existingId);
    }

    @Test
    void givenNoCorrelationId_whenFiltered_thenGeneratesValidUUID() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        String generated = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generated).isNotNull().matches("[0-9a-f\\-]{36}");
    }

    @Test
    void getOrder_returnsNegative200_ensuringItRunsFirst() {
        assertThat(filter.getOrder()).isEqualTo(-200);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void givenBlankCorrelationId_whenFiltered_thenGeneratesNewOne() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseId).isNotNull().isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void givenEmptyCorrelationId_whenFiltered_thenGeneratesNewOne() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        String responseId = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseId).isNotNull().isNotEmpty();
    }

    @Test
    void givenAnyRequest_whenFiltered_thenCorrelationIdAlwaysAppearsInResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/orders/99").build());

        StepVerifier.create(filter.filter(exchange, passThroughChain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().containsKey(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isTrue();
    }
}
