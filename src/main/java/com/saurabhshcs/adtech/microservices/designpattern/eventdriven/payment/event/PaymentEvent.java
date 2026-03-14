package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sealed domain events for the Payment bounded context.
 * Event-Driven pattern — Payment / Banking domain.
 */
public sealed interface PaymentEvent permits
        PaymentEvent.PaymentInitiated,
        PaymentEvent.PaymentCompleted,
        PaymentEvent.PaymentFailed {

    UUID paymentId();
    String customerId();
    Instant occurredAt();

    record PaymentInitiated(UUID paymentId, String customerId,
                            BigDecimal amount, String currency,
                            Instant occurredAt) implements PaymentEvent {}

    record PaymentCompleted(UUID paymentId, String customerId,
                            BigDecimal amount, String transactionRef,
                            Instant occurredAt) implements PaymentEvent {}

    record PaymentFailed(UUID paymentId, String customerId,
                         BigDecimal amount, String reason,
                         Instant occurredAt) implements PaymentEvent {}
}
