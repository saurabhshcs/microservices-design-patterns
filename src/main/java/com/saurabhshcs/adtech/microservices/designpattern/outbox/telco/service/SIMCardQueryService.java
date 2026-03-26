package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository.SIMCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Read-side service for SIM card and outbox queries.
 *
 * <p>In a full CQRS setup this would read from a separate read model / read replica.
 * For the outbox pattern demo it reads directly from the in-memory stores.
 */
@Service
@RequiredArgsConstructor
public class SIMCardQueryService {

    private final SIMCardRepository simCardRepository;
    private final OutboxRepository outboxRepository;

    public SIMCard findById(String simId) {
        return simCardRepository.findById(simId)
                .orElseThrow(() -> new IllegalArgumentException("SIM card not found: " + simId));
    }

    public Optional<SIMCard> findByMsisdn(String msisdn) {
        return simCardRepository.findByMsisdn(msisdn);
    }

    public Optional<SIMCard> findByIccid(String iccid) {
        return simCardRepository.findByIccid(iccid);
    }

    /** Return all outbox messages that have not yet been relayed to the broker. */
    public List<OutboxMessage> findPendingOutboxMessages() {
        return outboxRepository.findPendingMessages();
    }

    /** Return all outbox messages regardless of status (for audit / ops dashboards). */
    public List<OutboxMessage> findAllOutboxMessages() {
        return outboxRepository.findAll();
    }

    public int countOutboxMessagesByStatus(OutboxStatus status) {
        return outboxRepository.countByStatus(status);
    }
}
