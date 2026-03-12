package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.handler;

import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event-driven handler: flags high-value payments for fraud review.
 * Completely decoupled from the payment service — reacts autonomously to events.
 */
@Slf4j
@Component
public class FraudDetectionHandler {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private final List<PaymentEvent.PaymentInitiated> flaggedPayments = new ArrayList<>();

    @EventListener
    public void onPaymentInitiated(PaymentEvent.PaymentInitiated event) {
        if (event.amount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            log.warn("[FRAUD] High-value payment flagged: id={} amount={} {}",
                    event.paymentId(), event.amount(), event.currency());
            flaggedPayments.add(event);
        } else {
            log.info("[FRAUD] Payment cleared: id={}", event.paymentId());
        }
    }

    public List<PaymentEvent.PaymentInitiated> getFlaggedPayments() {
        return Collections.unmodifiableList(flaggedPayments);
    }

    public boolean isFlagged(java.util.UUID paymentId) {
        return flaggedPayments.stream().anyMatch(e -> e.paymentId().equals(paymentId));
    }
}
