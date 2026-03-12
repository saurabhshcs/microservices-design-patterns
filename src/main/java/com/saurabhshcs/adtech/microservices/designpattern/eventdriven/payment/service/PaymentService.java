package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.service;

import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event.PaymentEvent;
import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.publisher.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes payment domain events; handlers react autonomously.
 * This service knows nothing about fraud detection or notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentEventPublisher eventPublisher;

    public UUID initiatePayment(String customerId, BigDecimal amount, String currency) {
        UUID paymentId = UUID.randomUUID();
        eventPublisher.publish(new PaymentEvent.PaymentInitiated(paymentId, customerId, amount, currency, Instant.now()));
        return paymentId;
    }

    public void completePayment(UUID paymentId, String customerId, BigDecimal amount) {
        String txRef = "TXN-" + paymentId.toString().substring(0, 8).toUpperCase();
        eventPublisher.publish(new PaymentEvent.PaymentCompleted(paymentId, customerId, amount, txRef, Instant.now()));
    }

    public void failPayment(UUID paymentId, String customerId, BigDecimal amount, String reason) {
        eventPublisher.publish(new PaymentEvent.PaymentFailed(paymentId, customerId, amount, reason, Instant.now()));
    }
}
