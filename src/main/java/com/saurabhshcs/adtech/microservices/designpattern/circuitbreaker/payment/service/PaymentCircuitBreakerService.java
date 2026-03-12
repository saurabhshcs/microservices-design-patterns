package com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.service;

import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.client.PaymentGatewayClient;
import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentRequest;
import com.saurabhshcs.adtech.microservices.designpattern.circuitbreaker.payment.model.PaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Circuit Breaker pattern — Banking / Payment domain.
 *
 * <p>Wraps calls to an unreliable external payment gateway with a Resilience4j
 * CircuitBreaker so that cascading failures are avoided. When the circuit opens
 * (failure-rate threshold breached), calls are short-circuited and a fallback
 * PENDING response is returned instead of letting threads pile up.</p>
 *
 * <pre>
 * States:
 *   CLOSED     → normal operation, failures counted in sliding window
 *   OPEN       → calls short-circuited, fallback returned immediately
 *   HALF_OPEN  → probe calls permitted; circuit closes/reopens based on result
 * </pre>
 */
@Slf4j
@Service
public class PaymentCircuitBreakerService {

    private static final String CB_NAME = "paymentGateway";

    private final PaymentGatewayClient gatewayClient;
    private final CircuitBreaker circuitBreaker;

    public PaymentCircuitBreakerService(PaymentGatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(RuntimeException.class)
                .build();
        this.circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker(CB_NAME);
    }

    public PaymentResponse processPayment(PaymentRequest request) {
        Supplier<PaymentResponse> decorated =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> gatewayClient.charge(request));
        try {
            return decorated.get();
        } catch (Exception ex) {
            log.warn("Circuit breaker fallback triggered for payment {}: {}", request.paymentId(), ex.getMessage());
            return PaymentResponse.fallback(request.paymentId());
        }
    }

    /** Exposes current circuit breaker state for monitoring / health checks. */
    public CircuitBreaker.State circuitBreakerState() {
        return circuitBreaker.getState();
    }
}
