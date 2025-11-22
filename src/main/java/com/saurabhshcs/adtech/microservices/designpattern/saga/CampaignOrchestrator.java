package com.saurabhshcs.adtech.microservices.designpattern.saga;

import com.saurabhshcs.adtech.microservices.designpattern.model.UserModel;
import com.saurabhshcs.adtech.microservices.designpattern.saga.common.OrchestratorState;

import java.util.UUID;

public class CampaignOrchestrator {

    UserModel  userModel = UserModel.builder()
                                    .userName("Saurabh Sharma")
                                    .email("saurabhshcs@gmail.com")
                                    .build();

    private static final String BUDGET_FAIL = "BUDGET_FAIL";
    private static final String INVENTORY_FAIL = "INVENTORY_FAIL";
    private static final String SCHEDULE_FAIL = "SCHEDULE_FAIL";
    private OrchestratorState state = OrchestratorState.STARTED;

    public OrchestratorState execute(UUID campaignId, Boolean budgetOk, Boolean inventoryOk, Boolean scheduleOk) {
        try {
            if (!budgetOk) throw new IllegalStateException(BUDGET_FAIL);
            state = OrchestratorState.BUDGET_VALIDATED;

            if (!inventoryOk) throw new IllegalStateException(INVENTORY_FAIL);
            state = OrchestratorState.INVENTORY_RESERVED;

            if (!scheduleOk) throw new IllegalStateException(SCHEDULE_FAIL);
            state = OrchestratorState.SCHEDULED;

            state = OrchestratorState.COMPLETED;
            return state;
        } catch (Exception e) {
            compensate(campaignId);
            state = OrchestratorState.FAILED;
            return state;
        }
    }

    private void compensate(UUID campaignId) {
        // In production: cancel inventory, release budget, unschedule
        // Kept minimal for training lab
    }

    public OrchestratorState currentState() {
        return state;
    }
}
