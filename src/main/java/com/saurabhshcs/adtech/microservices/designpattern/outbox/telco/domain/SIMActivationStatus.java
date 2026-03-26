package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.domain;

/**
 * Lifecycle states of a SIM card in the telco network.
 */
public enum SIMActivationStatus {
    /** SIM registered but not yet provisioned on the network. */
    PENDING,
    /** SIM is fully active and usable by the subscriber. */
    ACTIVE,
    /** SIM temporarily deactivated (e.g., non-payment, fraud hold). */
    SUSPENDED,
    /** SIM permanently deactivated; cannot be reactivated. */
    TERMINATED
}
