package com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.command;

/**
 * Sealed command hierarchy for SIM card operations.
 *
 * <p>Using sealed interfaces + records gives us compile-time exhaustive pattern matching
 * in the command handler's switch expression.
 */
public sealed interface SIMCardCommand
        permits SIMCardCommand.RegisterSIMCommand,
                SIMCardCommand.ActivateSIMCommand,
                SIMCardCommand.SuspendSIMCommand,
                SIMCardCommand.TerminateSIMCommand,
                SIMCardCommand.ReactivateSIMCommand {

    /** Register a new SIM with the telco system before network provisioning. */
    record RegisterSIMCommand(String iccid, String msisdn, String customerId) implements SIMCardCommand {}

    /** Provision the SIM on the network and allow the subscriber to make calls/data. */
    record ActivateSIMCommand(String simId) implements SIMCardCommand {}

    /** Temporarily deactivate the SIM (e.g., unpaid bill, fraud alert). */
    record SuspendSIMCommand(String simId, String reason) implements SIMCardCommand {}

    /** Permanently deactivate the SIM (e.g., contract end, porting out). */
    record TerminateSIMCommand(String simId, String reason) implements SIMCardCommand {}

    /** Restore a suspended SIM to active after the blocking condition is resolved. */
    record ReactivateSIMCommand(String simId) implements SIMCardCommand {}
}
