package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.event;

import java.time.Instant;

/**
 * Sealed domain event hierarchy for SIM card lifecycle changes.
 *
 * <p>These events are:
 * <ol>
 *   <li>Emitted by the {@code SIMCard} aggregate after each state mutation.</li>
 *   <li>Written atomically to the outbox table by {@code SIMCardCommandService}.</li>
 *   <li>Relayed to downstream consumers (billing, provisioning, notifications) by {@code OutboxRelayService}.</li>
 * </ol>
 */
public sealed interface SIMCardEvent
        permits SIMCardEvent.SIMRegisteredEvent,
                SIMCardEvent.SIMActivatedEvent,
                SIMCardEvent.SIMSuspendedEvent,
                SIMCardEvent.SIMTerminatedEvent,
                SIMCardEvent.SIMReactivatedEvent {

    /** Fired when a SIM is registered in the system, awaiting network provisioning. */
    record SIMRegisteredEvent(
            String simId,
            String iccid,
            String msisdn,
            String customerId,
            Instant occurredAt) implements SIMCardEvent {}

    /** Fired when a SIM is fully provisioned and the subscriber can use the service. */
    record SIMActivatedEvent(
            String simId,
            String msisdn,
            String customerId,
            Instant occurredAt) implements SIMCardEvent {}

    /** Fired when a SIM is suspended; billing pauses, network access is blocked. */
    record SIMSuspendedEvent(
            String simId,
            String msisdn,
            String customerId,
            String reason,
            Instant occurredAt) implements SIMCardEvent {}

    /** Fired when a SIM is permanently terminated; number is returned to pool. */
    record SIMTerminatedEvent(
            String simId,
            String msisdn,
            String customerId,
            String reason,
            Instant occurredAt) implements SIMCardEvent {}

    /** Fired when a suspended SIM is restored to active service. */
    record SIMReactivatedEvent(
            String simId,
            String msisdn,
            String customerId,
            Instant occurredAt) implements SIMCardEvent {}
}
