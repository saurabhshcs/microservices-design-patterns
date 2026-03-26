package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.outbox;

/**
 * Processing state of a message in the transactional outbox table.
 */
public enum OutboxStatus {
    /** Written by the command service; not yet picked up by the relay. */
    PENDING,
    /** Successfully published to the message broker by the relay. */
    PUBLISHED,
    /** Relay attempted to publish but received an exception from the broker. */
    FAILED
}
