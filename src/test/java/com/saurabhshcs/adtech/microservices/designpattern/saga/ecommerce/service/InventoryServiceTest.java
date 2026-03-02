package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;

import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link InventoryService}.
 * 3 positive and 3 negative scenarios.
 */
class InventoryServiceTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void execute_withSufficientStock_reservesAndSucceeds() {
        Order order = Order.create("CUST-1", "PROD-001", 5, new BigDecimal("50"));

        SagaResult result = inventoryService.execute(order);

        assertThat(result.isSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        assertThat(inventoryService.getAvailableStock("PROD-001")).isEqualTo(95);
    }

    @Test
    void compensate_afterReservation_releasesStock() {
        Order order = Order.create("CUST-2", "PROD-002", 10, new BigDecimal("20"));
        inventoryService.execute(order); // stock 25 → 15
        assertThat(inventoryService.getAvailableStock("PROD-002")).isEqualTo(15);

        inventoryService.compensate(order); // stock 15 → 25

        assertThat(inventoryService.getAvailableStock("PROD-002")).isEqualTo(25);
    }

    @Test
    void getAvailableStock_returnsCorrectStockLevel() {
        assertThat(inventoryService.getAvailableStock("PROD-004")).isEqualTo(5);
        assertThat(inventoryService.getAvailableStock("PROD-003")).isEqualTo(0);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void execute_withInsufficientStock_returnsFailure() {
        Order order = Order.create("CUST-3", "PROD-002", 30, new BigDecimal("10"));

        SagaResult result = inventoryService.execute(order);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("PROD-002");
    }

    @Test
    void execute_withOutOfStockProduct_returnsFailure() {
        Order order = Order.create("CUST-4", "PROD-003", 1, new BigDecimal("5"));

        SagaResult result = inventoryService.execute(order);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Available: 0");
    }

    @Test
    void compensate_withoutPriorReservation_returnsSuccessWithoutReleasingStock() {
        Order order = Order.create("CUST-5", "PROD-001", 10, new BigDecimal("100"));
        int stockBefore = inventoryService.getAvailableStock("PROD-001");

        // Compensate without ever calling execute — no reservation exists
        SagaResult result = inventoryService.compensate(order);

        assertThat(result.isSuccess()).isTrue();
        // Stock unchanged because there was nothing to release
        assertThat(inventoryService.getAvailableStock("PROD-001")).isEqualTo(stockBefore);
    }
}
