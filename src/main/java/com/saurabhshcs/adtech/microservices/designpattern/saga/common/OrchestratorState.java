package com.saurabhshcs.adtech.microservices.designpattern.saga.common;

public enum OrchestratorState {
    STARTED,
    BUDGET_VALIDATED,
    INVENTORY_RESERVED,
    SCHEDULED,
    FAILED,
    COMPLETED
}
