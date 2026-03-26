package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.publisher;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;

/**
 * Port for publishing outbox messages to a message broker.
 *
 * <p>Production implementations may target Kafka, RabbitMQ, AWS SNS, etc.
 * The {@link InMemoryMessageBrokerPublisher} is used in tests and the demo.
 */
public interface MessageBrokerPublisher {

    /**
     * Publish a single outbox message to the broker.
     *
     * @param message the outbox message whose payload and routing metadata should be sent
     * @throws RuntimeException if the broker is unavailable; the relay marks the message FAILED
     */
    void publish(OutboxMessage message);
}
