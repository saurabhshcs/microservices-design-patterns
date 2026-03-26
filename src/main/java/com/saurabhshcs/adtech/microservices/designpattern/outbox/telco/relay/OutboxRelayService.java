package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.relay;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.publisher.MessageBrokerPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox relay — the second half of the Transactional Outbox pattern.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Poll the outbox table for {@code PENDING} messages.</li>
 *   <li>Publish each message to the message broker via {@link MessageBrokerPublisher}.</li>
 *   <li>Mark the message {@code PUBLISHED} on success or {@code FAILED} on exception.</li>
 * </ol>
 *
 * <p>In production this service is triggered by a scheduled task (e.g., {@code @Scheduled})
 * or a dedicated CDC tool (Debezium) watching the outbox table for row inserts.
 *
 * <p><b>Idempotency:</b> Only {@code PENDING} rows are processed; already-published rows
 * are never re-sent, making repeated relay runs safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxRepository outboxRepository;
    private final MessageBrokerPublisher messageBrokerPublisher;

    /**
     * Relay all pending outbox messages to the message broker.
     *
     * @return the number of messages successfully published in this run
     */
    public int relayPendingMessages() {
        List<OutboxMessage> pending = outboxRepository.findPendingMessages();
        if (pending.isEmpty()) {
            log.debug("[RELAY] No pending outbox messages.");
            return 0;
        }

        log.info("[RELAY] Processing {} pending outbox message(s).", pending.size());
        int published = 0;

        for (OutboxMessage message : pending) {
            try {
                messageBrokerPublisher.publish(message);
                message.markPublished();
                outboxRepository.update(message);
                published++;
                log.info("[RELAY] Published messageId={} eventType={}", message.getMessageId(), message.getEventType());
            } catch (Exception ex) {
                message.markFailed();
                outboxRepository.update(message);
                log.error("[RELAY] Failed to publish messageId={} (attempt #{}): {}",
                        message.getMessageId(), message.getRetryCount(), ex.getMessage(), ex);
            }
        }

        log.info("[RELAY] Relay run complete: published={}, failed={}.", published, pending.size() - published);
        return published;
    }
}
