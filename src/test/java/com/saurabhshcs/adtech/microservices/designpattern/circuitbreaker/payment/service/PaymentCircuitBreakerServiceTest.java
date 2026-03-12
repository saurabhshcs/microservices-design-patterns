package com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.service;

import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.client.PaymentGatewayClient;
import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentRequest;
import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD unit tests for {@link PaymentCircuitBreakerService}.
 * 3 positive and 3 negative scenarios.
 */
class PaymentCircuitBreakerServiceTest {

    private PaymentGatewayClient gatewayClient;
    private PaymentCircuitBreakerService service;

    @BeforeEach
    void setUp() {
        gatewayClient = mock(PaymentGatewayClient.class);
        service = new PaymentCircuitBreakerService(gatewayClient);
    }

    private PaymentRequest request() {
        return PaymentRequest.of("CUST-001", new BigDecimal("250.00"), "GBP");
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void processPayment_whenGatewaySucceeds_returnSuccessResponse() {
        when(gatewayClient.charge(any())).thenAnswer(inv ->
                PaymentResponse.success(((PaymentRequest) inv.getArgument(0)).paymentId()));

        PaymentResponse response = service.processPayment(request());

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(gatewayClient, times(1)).charge(any());
    }

    @Test
    void processPayment_circuitIsClosedInitially() {
        when(gatewayClient.charge(any())).thenAnswer(inv ->
                PaymentResponse.success(((PaymentRequest) inv.getArgument(0)).paymentId()));
        service.processPayment(request());

        assertThat(service.circuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void processPayment_multipleSuccessfulCalls_circuitRemainsClose() {
        when(gatewayClient.charge(any())).thenAnswer(inv ->
                PaymentResponse.success(((PaymentRequest) inv.getArgument(0)).paymentId()));

        for (int i = 0; i < 5; i++) service.processPayment(request());

        assertThat(service.circuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(gatewayClient, times(5)).charge(any());
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void processPayment_whenGatewayThrows_returnsFallbackResponse() {
        when(gatewayClient.charge(any())).thenThrow(new RuntimeException("Gateway timeout"));

        PaymentResponse response = service.processPayment(request());

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).contains("unavailable");
    }

    @Test
    void processPayment_afterEnoughFailures_circuitOpens() {
        when(gatewayClient.charge(any())).thenThrow(new RuntimeException("Gateway down"));

        // Need 10 calls (sliding window size) with 100% failures to open circuit
        for (int i = 0; i < 10; i++) service.processPayment(request());

        assertThat(service.circuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void processPayment_whenCircuitOpen_returnsFallbackWithoutCallingGateway() {
        when(gatewayClient.charge(any())).thenThrow(new RuntimeException("Down"));
        // Open the circuit
        for (int i = 0; i < 10; i++) service.processPayment(request());

        // Reset mock count
        reset(gatewayClient);

        PaymentResponse response = service.processPayment(request());

        assertThat(response.status()).isEqualTo("PENDING");
        // Gateway must NOT be called when circuit is open
        verify(gatewayClient, never()).charge(any());
    }
}
