package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.service;

import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event.PaymentEvent;
import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.handler.FraudDetectionHandler;
import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.handler.NotificationHandler;
import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.publisher.PaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD unit tests for event-driven payment flow.
 * 3 positive and 3 negative scenarios.
 */
class PaymentEventDrivenTest {

    private FraudDetectionHandler fraudHandler;
    private NotificationHandler notificationHandler;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        fraudHandler = new FraudDetectionHandler();
        notificationHandler = new NotificationHandler();

        // Wire a real event publisher that calls handlers directly
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof PaymentEvent.PaymentInitiated e) fraudHandler.onPaymentInitiated(e);
            if (event instanceof PaymentEvent.PaymentCompleted e) notificationHandler.onPaymentCompleted(e);
            if (event instanceof PaymentEvent.PaymentFailed e) notificationHandler.onPaymentFailed(e);
        };

        PaymentEventPublisher paymentEventPublisher = new PaymentEventPublisher(publisher);
        paymentService = new PaymentService(paymentEventPublisher);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void initiatePayment_belowThreshold_isNotFlaggedByFraudHandler() {
        UUID id = paymentService.initiatePayment("CUST-01", new BigDecimal("500.00"), "GBP");

        assertThat(fraudHandler.isFlagged(id)).isFalse();
        assertThat(fraudHandler.getFlaggedPayments()).isEmpty();
    }

    @Test
    void completePayment_triggersNotification() {
        UUID id = UUID.randomUUID();
        paymentService.completePayment(id, "CUST-02", new BigDecimal("250.00"));

        assertThat(notificationHandler.getSentNotifications()).hasSize(1);
        assertThat(notificationHandler.getSentNotifications().get(0)).contains("CUST-02");
    }

    @Test
    void failPayment_triggersFailureNotification() {
        UUID id = UUID.randomUUID();
        paymentService.failPayment(id, "CUST-03", new BigDecimal("100.00"), "Insufficient funds");

        assertThat(notificationHandler.getSentNotifications()).hasSize(1);
        assertThat(notificationHandler.getSentNotifications().get(0)).contains("Insufficient funds");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void initiatePayment_aboveThreshold_isFlaggedForFraud() {
        UUID id = paymentService.initiatePayment("CUST-04", new BigDecimal("15000.00"), "GBP");

        assertThat(fraudHandler.isFlagged(id)).isTrue();
        assertThat(fraudHandler.getFlaggedPayments()).hasSize(1);
    }

    @Test
    void initiatePayment_exactlyAtThreshold_isFlagged() {
        UUID id = paymentService.initiatePayment("CUST-05", new BigDecimal("10000.00"), "USD");

        assertThat(fraudHandler.isFlagged(id)).isTrue();
    }

    @Test
    void failPayment_doesNotTriggerFraudHandler() {
        UUID id = UUID.randomUUID();
        paymentService.failPayment(id, "CUST-06", new BigDecimal("50000.00"), "Card declined");

        // Fraud handler only listens to PaymentInitiated — failed payment does not trigger it
        assertThat(fraudHandler.isFlagged(id)).isFalse();
        assertThat(fraudHandler.getFlaggedPayments()).isEmpty();
    }
}
