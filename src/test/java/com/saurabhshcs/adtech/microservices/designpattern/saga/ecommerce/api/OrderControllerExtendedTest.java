package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.api;

import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.orchestrator.OrderSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extended TDD unit tests for {@link OrderController}.
 * Covers boundary values, all failure statuses, and response-body assertions
 * not present in {@link OrderControllerTest}.
 * 3 positive and 3 negative scenarios.
 */
class OrderControllerExtendedTest {

    private OrderSagaOrchestrator orchestrator;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        orchestrator = mock(OrderSagaOrchestrator.class);
        client = WebTestClient.bindToController(new OrderController(orchestrator)).build();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> validBody(String customerId, String productId,
                                          int quantity, double amount) {
        return Map.of("customerId", customerId, "productId", productId,
                "quantity", quantity, "amount", amount);
    }

    private Order orderWithStatus(OrderStatus status) {
        Order o = Order.create("CUST-EXT", "PROD-EXT", 1, new BigDecimal("49.99"));
        o.updateStatus(status);
        return o;
    }

    private Order failedOrderWithReason(String reason) {
        Order o = Order.create("CUST-EXT", "PROD-EXT", 1, new BigDecimal("10.00"));
        o.fail(reason);
        return o;
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    /**
     * Boundary: minimum valid amount (0.01) and quantity (1) must succeed.
     */
    @Test
    void createOrder_withMinimumBoundaryValues_returns201() {
        when(orchestrator.processOrder(any())).thenReturn(orderWithStatus(OrderStatus.COMPLETED));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-MIN", "PROD-MIN", 1, 0.01))
                .exchange()
                .expectStatus().isEqualTo(201);
    }

    /**
     * Response body must contain a non-null UUID orderId.
     */
    @Test
    void createOrder_responseBody_containsNonNullOrderId() {
        when(orchestrator.processOrder(any())).thenReturn(orderWithStatus(OrderStatus.COMPLETED));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-ID", "PROD-001", 3, 150.00))
                .exchange()
                .expectStatus().isEqualTo(201)
                .expectBody()
                .jsonPath("$.orderId").value(id -> assertThat(id.toString()).isNotBlank());
    }

    /**
     * Response body must echo back the customerId and productId from the request.
     */
    @Test
    void createOrder_responseBody_echoesCustomerAndProduct() {
        Order completed = Order.create("CUST-ECHO", "PROD-ECHO", 2, new BigDecimal("75.00"));
        completed.updateStatus(OrderStatus.COMPLETED);
        when(orchestrator.processOrder(any())).thenReturn(completed);

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-ECHO", "PROD-ECHO", 2, 75.00))
                .exchange()
                .expectStatus().isEqualTo(201)
                .expectBody()
                .jsonPath("$.customerId").isEqualTo("CUST-ECHO")
                .jsonPath("$.productId").isEqualTo("PROD-ECHO");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    /**
     * SHIPPING_FAILED is not COMPLETED → must return 422.
     */
    @Test
    void createOrder_withShippingFailedStatus_returns422() {
        when(orchestrator.processOrder(any())).thenReturn(orderWithStatus(OrderStatus.SHIPPING_FAILED));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-SF", "PROD-SF", 1, 20.00))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    /**
     * When the orchestrator marks the order as FAILED via {@link Order#fail(String)},
     * the response must be 422 and include the failure reason.
     */
    @Test
    void createOrder_withFailedStatus_responseContainsFailureReason() {
        when(orchestrator.processOrder(any())).thenReturn(failedOrderWithReason("Payment gateway timeout"));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-FR", "PROD-FR", 1, 30.00))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.failureReason").isEqualTo("Payment gateway timeout");
    }

    /**
     * COMPENSATION_COMPLETED is a terminal failure state → must return 422.
     */
    @Test
    void createOrder_withCompensationCompletedStatus_returns422() {
        when(orchestrator.processOrder(any())).thenReturn(orderWithStatus(OrderStatus.COMPENSATION_COMPLETED));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody("CUST-CC", "PROD-CC", 2, 60.00))
                .exchange()
                .expectStatus().isEqualTo(422);
    }
}
