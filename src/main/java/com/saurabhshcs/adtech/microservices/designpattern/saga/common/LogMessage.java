package com.saurabhshcs.adtech.microservices.designpattern.saga.common;

public enum LogMessage {
    ORCHESTRATION_STARTED("Starting campaign orchestration for campaignId: {}"),
    BUDGET_VALIDATED("Budget validated for campaignId: {}"),
    INVENTORY_RESERVED("Inventory reserved for campaignId: {}"),
    CAMPAIGN_SCHEDULED("Campaign scheduled for campaignId: {}"),
    ORCHESTRATION_COMPLETED("Campaign orchestration completed successfully for campaignId: {}"),
    ORCHESTRATION_FAILED("Campaign orchestration failed for campaignId: {}. Error: {}"),
    COMPENSATION_TRIGGERED("Compensating transaction for campaignId: {}");

    private final String message;

    LogMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }
}
