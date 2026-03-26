package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * A single row in the transactional outbox table.
 *
 * <p>Created atomically alongside the business entity update (same DB transaction).
 * The {@link com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.relay.OutboxRelayService}
 * polls for {@code PENDING} rows and publishes them to the message broker.
 */
@Getter
@Builder
public class OutboxMessage {

    /** Unique message identifier (UUID). */
    private final String messageId;

    /** Business entity ID that caused this event (e.g., SIM card ID). */
    private final String aggregateId;

    /** Type of the business aggregate (e.g., {@code "SIMCard"}). */
    private final String aggregateType;

    /** Simple class name of the domain event (e.g., {@code "SIMActivatedEvent"}). */
    private final String eventType;

    /** JSON-serialized event payload for the message broker. */
    private final String payload;

    /** Current processing state of this outbox record. */
    private OutboxStatus status;

    /** Timestamp when this row was written to the outbox. */
    private final Instant createdAt;

    /** Timestamp of the last relay attempt (null if not yet attempted). */
    private Instant processedAt;

    /** Number of failed relay attempts; used for dead-letter decisions. */
    @Builder.Default
    private int retryCount = 0;

    // -------------------------------------------------------------------------
    // State transitions (called by the relay service)
    // -------------------------------------------------------------------------

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.processedAt = Instant.now();
    }
}
