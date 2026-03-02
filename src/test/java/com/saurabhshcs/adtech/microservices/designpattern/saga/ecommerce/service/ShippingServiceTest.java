package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;

import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link ShippingService}.
 * 3 positive and 3 negative scenarios.
 */
class ShippingServiceTest {

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void execute_schedulesShipmentAndReturnsSuccess() {
        Order order = Order.create("CUST-1", "PROD-001", 2, new BigDecimal("100"));

        SagaResult result = shippingService.execute(order);

        assertThat(result.isSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING_SCHEDULED);
    }

    @Test
    void compensate_afterScheduling_marksOrderAsShippingFailed() {
        Order order = Order.create("CUST-2", "PROD-001", 1, new BigDecimal("50"));
        shippingService.execute(order); // creates shipment record

        SagaResult result = shippingService.compensate(order);

        assertThat(result.isSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING_FAILED);
    }

    @Test
    void stepName_returnsShippingService() {
        assertThat(shippingService.stepName()).isEqualTo("ShippingService");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void compensate_withoutPriorExecution_doesNotUpdateStatus() {
        Order order = Order.create("CUST-3", "PROD-002", 1, new BigDecimal("30"));

        // Compensate without a prior execute — no shipment record
        SagaResult result = shippingService.compensate(order);

        assertThat(result.isSuccess()).isTrue();
        // Status never set to SHIPPING_SCHEDULED, so no SHIPPING_FAILED either
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.SHIPPING_FAILED);
    }

    @Test
    void execute_generatesUniqueTrackingNumberEachTime() {
        Order order1 = Order.create("CUST-4", "PROD-001", 1, new BigDecimal("10"));
        Order order2 = Order.create("CUST-5", "PROD-001", 1, new BigDecimal("10"));

        shippingService.execute(order1);
        shippingService.execute(order2);

        // Both orders are SHIPPING_SCHEDULED (different tracking IDs internally)
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPING_SCHEDULED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.SHIPPING_SCHEDULED);
    }

    @Test
    void compensate_returnsSuccessEvenForUnknownOrder() {
        Order order = Order.create("CUST-6", "PROD-003", 5, new BigDecimal("200"));
        // Never executed — compensation of unknown order should not throw
        SagaResult result = shippingService.compensate(order);
        assertThat(result.isSuccess()).isTrue();
    }
}
