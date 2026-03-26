package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.publisher;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory broker publisher for demo and integration testing.
 *
 * <p>Simulates a message broker by collecting published messages in a thread-safe list.
 * In a real telco deployment this would be replaced by a Kafka producer, pushing events to
 * topics such as {@code sim.activated}, {@code sim.suspended}, etc.
 */
@Slf4j
@Component
public class InMemoryMessageBrokerPublisher implements MessageBrokerPublisher {

    @Getter
    private final List<OutboxMessage> publishedMessages = new CopyOnWriteArrayList<>();

    @Override
    public void publish(OutboxMessage message) {
        log.info("[BROKER] Publishing event: type={}, aggregateId={}, messageId={}",
                message.getEventType(), message.getAggregateId(), message.getMessageId());
        publishedMessages.add(message);
    }

    /** Reset the published message log (useful between test cases). */
    public void clear() {
        publishedMessages.clear();
    }
}
