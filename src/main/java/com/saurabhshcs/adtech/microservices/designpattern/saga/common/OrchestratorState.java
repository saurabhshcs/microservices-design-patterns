package com.saurabhshcs.adtech.microservices.designpattern.saga.common;

/**
 * Represents the lifecycle states of a saga orchestration.
 * <p>
 * Each state carries a human-readable description for logging and API responses.
 * Terminal states ({@link #COMPLETED} and {@link #FAILED}) indicate the saga is done.
 * </p>
 */
public enum OrchestratorState {

    STARTED("Saga orchestration has begun"),
    BUDGET_VALIDATED("Budget check passed successfully"),
    INVENTORY_RESERVED("Inventory has been reserved"),
    SCHEDULED("Campaign has been scheduled"),
    COMPLETED("Saga completed successfully"),
    FAILED("Saga failed — compensation triggered");

    private final String description;

    OrchestratorState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Returns {@code true} if this state represents a terminal (no further transitions) outcome. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /** Returns {@code true} only when the saga reached {@link #COMPLETED}. */
    public boolean isSuccess() {
        return this == COMPLETED;
    }
}
