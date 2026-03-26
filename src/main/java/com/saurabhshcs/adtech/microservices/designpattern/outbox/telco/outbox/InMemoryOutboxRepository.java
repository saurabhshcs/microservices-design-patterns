package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link OutboxRepository}.
 *
 * <p>Intended for demo and unit tests only. In production replace with a JPA/JDBC
 * repository backed by the same transactional database as the business data.
 */
@Repository
public class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<String, OutboxMessage> store = new ConcurrentHashMap<>();

    @Override
    public void save(OutboxMessage message) {
        store.put(message.getMessageId(), message);
    }

    @Override
    public List<OutboxMessage> findPendingMessages() {
        return store.values().stream()
                .filter(m -> m.getStatus() == OutboxStatus.PENDING)
                .collect(Collectors.toList());
    }

    @Override
    public List<OutboxMessage> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(OutboxMessage message) {
        store.put(message.getMessageId(), message);
    }

    @Override
    public int countByStatus(OutboxStatus status) {
        return (int) store.values().stream()
                .filter(m -> m.getStatus() == status)
                .count();
    }
}
