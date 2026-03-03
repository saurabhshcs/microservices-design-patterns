package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.eventstore;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link EventStore}.
 * <p>
 * Suitable for the training lab. Replace with a durable store
 * (EventStoreDB, PostgreSQL with JSONB, Kafka) for production use.
 * </p>
 */
@Component
public class InMemoryEventStore implements EventStore {

    /** Aggregate ID → ordered list of all events for that aggregate. */
    private final Map<UUID, List<AccountEvent>> store = new ConcurrentHashMap<>();

    @Override
    public void append(UUID aggregateId, List<AccountEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        store.computeIfAbsent(aggregateId, id -> new ArrayList<>()).addAll(events);
    }

    @Override
    public List<AccountEvent> loadEvents(UUID aggregateId) {
        return Collections.unmodifiableList(
                store.getOrDefault(aggregateId, Collections.emptyList()));
    }

    @Override
    public List<AccountEvent> loadEventsSince(UUID aggregateId, Instant since) {
        return loadEvents(aggregateId).stream()
                .filter(e -> !e.occurredAt().isBefore(since))
                .toList();
    }

    @Override
    public List<AccountEvent> loadAllEvents() {
        return store.values().stream()
                .flatMap(List::stream)
                .sorted((a, b) -> a.occurredAt().compareTo(b.occurredAt()))
                .toList();
    }
}
