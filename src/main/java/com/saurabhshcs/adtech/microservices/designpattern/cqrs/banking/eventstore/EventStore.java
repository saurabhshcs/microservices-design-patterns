package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.eventstore;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event Store — the append-only log of domain events for Event Sourcing.
 * <p>
 * In a production system this would be backed by a database such as EventStoreDB,
 * PostgreSQL, or Apache Kafka. Here we provide an in-memory implementation
 * suitable for the training lab.
 * </p>
 *
 * <pre>
 * Write side  →  AccountCommandService  →  EventStore.append()
 * Read  side  →  AccountProjection      ←  EventStore.loadEvents()
 * </pre>
 */
public interface EventStore {

    /**
     * Appends a batch of events for a given aggregate.
     * Events must be appended in the order they occurred.
     *
     * @param aggregateId the account UUID
     * @param events      domain events produced by the aggregate
     */
    void append(UUID aggregateId, List<AccountEvent> events);

    /**
     * Returns all events ever recorded for an aggregate, oldest first.
     *
     * @param aggregateId the account UUID
     * @return immutable ordered list; empty if the aggregate has no events
     */
    List<AccountEvent> loadEvents(UUID aggregateId);

    /**
     * Returns events for an aggregate that occurred at or after {@code since}.
     * Useful for partial replay and catch-up projections.
     *
     * @param aggregateId the account UUID
     * @param since       inclusive lower bound
     * @return filtered, ordered list of events
     */
    List<AccountEvent> loadEventsSince(UUID aggregateId, Instant since);

    /**
     * Returns every event across all aggregates, oldest first.
     * Intended for full-rebuild projections during startup.
     *
     * @return immutable global event stream
     */
    List<AccountEvent> loadAllEvents();
}
