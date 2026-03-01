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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link OrderController}.
 * 3 positive and 3 negative scenarios.
 */
class OrderControllerTest {

    private OrderSagaOrchestrator orchestrator;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        orchestrator = mock(OrderSagaOrchestrator.class);
        client = WebTestClient.bindToController(new OrderController(orchestrator)).build();
    }

    private Order completedOrder() {
        Order o = Order.create("CUST-1", "PROD-001", 2, new BigDecimal("200"));
        o.updateStatus(OrderStatus.COMPLETED);
        return o;
    }

    private Order failedOrder() {
        Order o = Order.create("CUST-1", "PROD-003", 10, new BigDecimal("50"));
        o.updateStatus(OrderStatus.PAYMENT_FAILED);
        return o;
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void createOrder_whenCompleted_returns201() {
        when(orchestrator.processOrder(any())).thenReturn(completedOrder());

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-1", "productId", "PROD-001",
                        "quantity", 2, "amount", 200))
                .exchange()
                .expectStatus().isEqualTo(201);
    }

    @Test
    void createOrder_responseBodyContainsOrderDetails() {
        Order completed = completedOrder();
        when(orchestrator.processOrder(any())).thenReturn(completed);

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-1", "productId", "PROD-001",
                        "quantity", 1, "amount", 100))
                .exchange()
                .expectStatus().isEqualTo(201)
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void createOrder_callsOrchestratorOnce() {
        when(orchestrator.processOrder(any())).thenReturn(completedOrder());

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-1", "productId", "PROD-001",
                        "quantity", 1, "amount", 99))
                .exchange();

        verify(orchestrator, times(1)).processOrder(any());
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void createOrder_whenFailed_returns422() {
        when(orchestrator.processOrder(any())).thenReturn(failedOrder());

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-1", "productId", "PROD-003",
                        "quantity", 10, "amount", 50))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void createOrder_withInventoryFailed_returns422() {
        Order inventoryFailed = Order.create("CUST-2", "PROD-003", 5, new BigDecimal("30"));
        inventoryFailed.updateStatus(OrderStatus.INVENTORY_FAILED);
        when(orchestrator.processOrder(any())).thenReturn(inventoryFailed);

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-2", "productId", "PROD-003",
                        "quantity", 5, "amount", 30))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void createOrder_whenOrchestratorThrows_returns5xx() {
        when(orchestrator.processOrder(any())).thenThrow(new RuntimeException("Unexpected error"));

        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "CUST-X", "productId", "PROD-001",
                        "quantity", 1, "amount", 10))
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
