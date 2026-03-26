package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox;

import java.util.List;

/**
 * Port for accessing the outbox table.
 *
 * <p>In production this maps to a real database table. In tests and the demo the
 * {@link InMemoryOutboxRepository} implementation is used.
 */
public interface OutboxRepository {

    /** Persist a new outbox message (always in {@code PENDING} status). */
    void save(OutboxMessage message);

    /** Return all messages in {@code PENDING} status for the relay to process. */
    List<OutboxMessage> findPendingMessages();

    /** Return every message regardless of status (useful for auditing / tests). */
    List<OutboxMessage> findAll();

    /** Persist status and timestamp changes made by the relay service. */
    void update(OutboxMessage message);

    /** Count messages by status — primarily used in tests and monitoring. */
    int countByStatus(OutboxStatus status);
}
