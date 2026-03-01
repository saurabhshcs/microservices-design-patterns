package com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.service;

import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.domain.OrderStatus;
import com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.saga.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link PaymentService}.
 * 3 positive and 3 negative scenarios.
 */
class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void execute_withAmountWithinLimit_succeeds() {
        Order order = Order.create("CUST-1", "PROD-001", 1, new BigDecimal("500"));

        SagaResult result = paymentService.execute(order);

        assertThat(result.isSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
    }

    @Test
    void execute_withAmountExactlyAtLimit_succeeds() {
        Order order = Order.create("CUST-2", "PROD-001", 1, new BigDecimal("10000"));

        SagaResult result = paymentService.execute(order);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void compensate_afterSuccessfulPayment_marksOrderAsFailed() {
        Order order = Order.create("CUST-3", "PROD-001", 1, new BigDecimal("100"));
        paymentService.execute(order); // creates the payment record

        SagaResult result = paymentService.compensate(order);

        assertThat(result.isSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void execute_withAmountExceedingLimit_returnsFailure() {
        Order order = Order.create("CUST-4", "PROD-001", 1, new BigDecimal("10000.01"));

        SagaResult result = paymentService.execute(order);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("10000");
    }

    @Test
    void execute_withVeryLargeAmount_returnsFailure() {
        Order order = Order.create("CUST-5", "PROD-001", 1, new BigDecimal("99999"));

        SagaResult result = paymentService.execute(order);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void compensate_withoutPriorPayment_returnsSuccessWithoutStatusChange() {
        Order order = Order.create("CUST-6", "PROD-001", 1, new BigDecimal("200"));
        // Do NOT call execute — no payment record exists

        SagaResult result = paymentService.compensate(order);

        assertThat(result.isSuccess()).isTrue();
        // Status was never set to PAYMENT_COMPLETED, so it stays as initial
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.PAYMENT_FAILED);
    }
}
