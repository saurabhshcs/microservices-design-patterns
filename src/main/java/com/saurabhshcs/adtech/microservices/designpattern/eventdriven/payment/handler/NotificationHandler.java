package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.handler;

import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Event-driven handler: sends customer notifications on payment outcomes.
 */
@Slf4j
@Component
public class NotificationHandler {

    private final List<String> sentNotifications = new ArrayList<>();

    @EventListener
    public void onPaymentCompleted(PaymentEvent.PaymentCompleted event) {
        String msg = "Payment %s confirmed for customer %s".formatted(event.transactionRef(), event.customerId());
        log.info("[NOTIFY] {}", msg);
        sentNotifications.add(msg);
    }

    @EventListener
    public void onPaymentFailed(PaymentEvent.PaymentFailed event) {
        String msg = "Payment failed for customer %s: %s".formatted(event.customerId(), event.reason());
        log.warn("[NOTIFY] {}", msg);
        sentNotifications.add(msg);
    }

    public List<String> getSentNotifications() {
        return Collections.unmodifiableList(sentNotifications);
    }
}
