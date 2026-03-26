package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link SIMCardRepository}.
 *
 * <p>Replace with a JPA/JDBC repository in production, ensuring that
 * saves to this store and the outbox table share the same database transaction.
 */
@Repository
public class InMemorySIMCardRepository implements SIMCardRepository {

    private final Map<String, SIMCard> store = new ConcurrentHashMap<>();

    @Override
    public void save(SIMCard simCard) {
        store.put(simCard.getSimId(), simCard);
    }

    @Override
    public Optional<SIMCard> findById(String simId) {
        return Optional.ofNullable(store.get(simId));
    }

    @Override
    public Optional<SIMCard> findByMsisdn(String msisdn) {
        return store.values().stream()
                .filter(s -> s.getMsisdn().equals(msisdn))
                .findFirst();
    }

    @Override
    public Optional<SIMCard> findByIccid(String iccid) {
        return store.values().stream()
                .filter(s -> s.getIccid().equals(iccid))
                .findFirst();
    }
}
