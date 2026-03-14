package com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.publisher;

import com.saurabhshcs.adtech.microservices.designpattern.eventdriven.payment.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes payment domain events via Spring's in-process event bus.
 * In production, replace with Kafka / RabbitMQ publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(PaymentEvent event) {
        log.info("Publishing event: {} for payment={}", event.getClass().getSimpleName(), event.paymentId());
        eventPublisher.publishEvent(event);
    }
}
