package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.command.SIMCardCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain.SIMCard;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.event.SIMCardEvent;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxMessage;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox.OutboxStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.repository.SIMCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write-side service for SIM card operations.
 *
 * <p><b>Transactional Outbox guarantee</b>: every command handler must:
 * <ol>
 *   <li>Mutate the {@link SIMCard} aggregate and persist it via {@link SIMCardRepository}.</li>
 *   <li>Drain pending domain events and write each to the outbox via {@link OutboxRepository}.</li>
 * </ol>
 * Both operations happen in the same logical "transaction". In production, wrap the method body
 * in {@code @Transactional} and ensure both repositories share the same {@code DataSource}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SIMCardCommandService {

    private final SIMCardRepository simCardRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SIMCard handle(SIMCardCommand command) {
        return switch (command) {
            case SIMCardCommand.RegisterSIMCommand cmd  -> registerSIM(cmd);
            case SIMCardCommand.ActivateSIMCommand cmd  -> activateSIM(cmd);
            case SIMCardCommand.SuspendSIMCommand cmd   -> suspendSIM(cmd);
            case SIMCardCommand.TerminateSIMCommand cmd -> terminateSIM(cmd);
            case SIMCardCommand.ReactivateSIMCommand cmd -> reactivateSIM(cmd);
        };
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    private SIMCard registerSIM(SIMCardCommand.RegisterSIMCommand cmd) {
        SIMCard sim = SIMCard.register(cmd.iccid(), cmd.msisdn(), cmd.customerId());
        persistAtomically(sim);
        log.info("SIM registered: simId={}, msisdn={}", sim.getSimId(), sim.getMsisdn());
        return sim;
    }

    private SIMCard activateSIM(SIMCardCommand.ActivateSIMCommand cmd) {
        SIMCard sim = findOrThrow(cmd.simId());
        sim.activate();
        persistAtomically(sim);
        log.info("SIM activated: simId={}, msisdn={}", sim.getSimId(), sim.getMsisdn());
        return sim;
    }

    private SIMCard suspendSIM(SIMCardCommand.SuspendSIMCommand cmd) {
        SIMCard sim = findOrThrow(cmd.simId());
        sim.suspend(cmd.reason());
        persistAtomically(sim);
        log.info("SIM suspended: simId={}, reason={}", sim.getSimId(), cmd.reason());
        return sim;
    }

    private SIMCard terminateSIM(SIMCardCommand.TerminateSIMCommand cmd) {
        SIMCard sim = findOrThrow(cmd.simId());
        sim.terminate(cmd.reason());
        persistAtomically(sim);
        log.info("SIM terminated: simId={}, reason={}", sim.getSimId(), cmd.reason());
        return sim;
    }

    private SIMCard reactivateSIM(SIMCardCommand.ReactivateSIMCommand cmd) {
        SIMCard sim = findOrThrow(cmd.simId());
        sim.reactivate();
        persistAtomically(sim);
        log.info("SIM reactivated: simId={}", sim.getSimId());
        return sim;
    }

    // -------------------------------------------------------------------------
    // Outbox write — the core of the pattern
    // -------------------------------------------------------------------------

    /**
     * Atomically persist the aggregate and write outbox messages for all pending events.
     * In production this whole method must execute inside a single DB transaction.
     */
    private void persistAtomically(SIMCard sim) {
        simCardRepository.save(sim);
        List<SIMCardEvent> events = sim.drainPendingEvents();
        events.forEach(this::writeOutboxMessage);
    }

    private void writeOutboxMessage(SIMCardEvent event) {
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .aggregateId(aggregateIdOf(event))
                .aggregateType("SIMCard")
                .eventType(event.getClass().getSimpleName())
                .payload(serialize(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(outboxMessage);
        log.debug("Outbox message written: messageId={}, eventType={}",
                outboxMessage.getMessageId(), outboxMessage.getEventType());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SIMCard findOrThrow(String simId) {
        return simCardRepository.findById(simId)
                .orElseThrow(() -> new IllegalArgumentException("SIM card not found: " + simId));
    }

    private String aggregateIdOf(SIMCardEvent event) {
        return switch (event) {
            case SIMCardEvent.SIMRegisteredEvent e  -> e.simId();
            case SIMCardEvent.SIMActivatedEvent e   -> e.simId();
            case SIMCardEvent.SIMSuspendedEvent e   -> e.simId();
            case SIMCardEvent.SIMTerminatedEvent e  -> e.simId();
            case SIMCardEvent.SIMReactivatedEvent e -> e.simId();
        };
    }

    private String serialize(SIMCardEvent event) {
        Map<String, Object> payload = switch (event) {
            case SIMCardEvent.SIMRegisteredEvent e -> Map.of(
                    "simId", e.simId(), "iccid", e.iccid(), "msisdn", e.msisdn(),
                    "customerId", e.customerId(), "occurredAt", e.occurredAt().toString());
            case SIMCardEvent.SIMActivatedEvent e -> Map.of(
                    "simId", e.simId(), "msisdn", e.msisdn(),
                    "customerId", e.customerId(), "occurredAt", e.occurredAt().toString());
            case SIMCardEvent.SIMSuspendedEvent e -> Map.of(
                    "simId", e.simId(), "msisdn", e.msisdn(), "customerId", e.customerId(),
                    "reason", e.reason(), "occurredAt", e.occurredAt().toString());
            case SIMCardEvent.SIMTerminatedEvent e -> Map.of(
                    "simId", e.simId(), "msisdn", e.msisdn(), "customerId", e.customerId(),
                    "reason", e.reason(), "occurredAt", e.occurredAt().toString());
            case SIMCardEvent.SIMReactivatedEvent e -> Map.of(
                    "simId", e.simId(), "msisdn", e.msisdn(),
                    "customerId", e.customerId(), "occurredAt", e.occurredAt().toString());
        };
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize event: " + event.getClass().getSimpleName(), ex);
        }
    }
}
