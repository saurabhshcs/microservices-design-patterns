package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;

import java.util.Optional;

/**
 * Port for persisting and retrieving {@link SIMCard} aggregates.
 *
 * <p>In production this maps to a JPA repository backed by PostgreSQL (or equivalent).
 * In the demo the {@link InMemorySIMCardRepository} is used.
 */
public interface SIMCardRepository {

    void save(SIMCard simCard);

    Optional<SIMCard> findById(String simId);

    Optional<SIMCard> findByMsisdn(String msisdn);

    Optional<SIMCard> findByIccid(String iccid);
}
