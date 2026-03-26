package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.event.SIMCardEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root representing a physical SIM card in the telco network.
 *
 * <p>Domain rules:
 * <ul>
 *   <li>A SIM starts in {@code PENDING} after registration.</li>
 *   <li>Only a {@code PENDING} SIM can be activated.</li>
 *   <li>Only an {@code ACTIVE} SIM can be suspended.</li>
 *   <li>Only a {@code SUSPENDED} SIM can be reactivated.</li>
 *   <li>Any non-TERMINATED SIM can be terminated.</li>
 * </ul>
 *
 * <p>All state mutations produce domain events stored in {@code pendingEvents}.
 * Callers must drain events via {@link #drainPendingEvents()} after persisting the aggregate.
 */
@Getter
public class SIMCard {

    private final String simId;
    /** ICCID — 19-20 digit SIM serial number printed on the card. */
    private final String iccid;
    /** MSISDN — the subscriber's phone number (E.164 format). */
    private final String msisdn;
    private final String customerId;
    private SIMActivationStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private final List<SIMCardEvent> pendingEvents = new ArrayList<>();

    private SIMCard(String simId, String iccid, String msisdn, String customerId) {
        this.simId = simId;
        this.iccid = iccid;
        this.msisdn = msisdn;
        this.customerId = customerId;
        this.status = SIMActivationStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static SIMCard register(String iccid, String msisdn, String customerId) {
        if (iccid == null || iccid.isBlank())       throw new IllegalArgumentException("ICCID cannot be blank");
        if (msisdn == null || msisdn.isBlank())     throw new IllegalArgumentException("MSISDN cannot be blank");
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("Customer ID cannot be blank");

        String simId = UUID.randomUUID().toString();
        SIMCard sim = new SIMCard(simId, iccid, msisdn, customerId);
        sim.pendingEvents.add(new SIMCardEvent.SIMRegisteredEvent(simId, iccid, msisdn, customerId, sim.createdAt));
        return sim;
    }

    // -------------------------------------------------------------------------
    // Business operations
    // -------------------------------------------------------------------------

    public void activate() {
        requireStatus(SIMActivationStatus.PENDING, "activated");
        this.status = SIMActivationStatus.ACTIVE;
        this.updatedAt = Instant.now();
        pendingEvents.add(new SIMCardEvent.SIMActivatedEvent(simId, msisdn, customerId, updatedAt));
    }

    public void suspend(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Suspension reason cannot be blank");
        requireStatus(SIMActivationStatus.ACTIVE, "suspended");
        this.status = SIMActivationStatus.SUSPENDED;
        this.updatedAt = Instant.now();
        pendingEvents.add(new SIMCardEvent.SIMSuspendedEvent(simId, msisdn, customerId, reason, updatedAt));
    }

    public void reactivate() {
        requireStatus(SIMActivationStatus.SUSPENDED, "reactivated");
        this.status = SIMActivationStatus.ACTIVE;
        this.updatedAt = Instant.now();
        pendingEvents.add(new SIMCardEvent.SIMReactivatedEvent(simId, msisdn, customerId, updatedAt));
    }

    public void terminate(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Termination reason cannot be blank");
        if (status == SIMActivationStatus.TERMINATED) {
            throw new IllegalStateException("SIM " + simId + " is already terminated");
        }
        this.status = SIMActivationStatus.TERMINATED;
        this.updatedAt = Instant.now();
        pendingEvents.add(new SIMCardEvent.SIMTerminatedEvent(simId, msisdn, customerId, reason, updatedAt));
    }

    // -------------------------------------------------------------------------
    // Event sourcing helper
    // -------------------------------------------------------------------------

    /**
     * Returns all pending domain events and clears the internal list.
     * Must be called after persisting the aggregate to avoid re-publishing events.
     */
    public List<SIMCardEvent> drainPendingEvents() {
        List<SIMCardEvent> snapshot = Collections.unmodifiableList(new ArrayList<>(pendingEvents));
        pendingEvents.clear();
        return snapshot;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireStatus(SIMActivationStatus required, String operation) {
        if (status != required) {
            throw new IllegalStateException(
                    String.format("SIM %s cannot be %s from state: %s", simId, operation, status));
        }
    }
}
