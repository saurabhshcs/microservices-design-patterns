package com.saurabhshcs.adtech.microservices.designpattern.saga;

import com.saurabhshcs.adtech.microservices.designpattern.model.UserModel;
import com.saurabhshcs.adtech.microservices.designpattern.saga.common.LogMessage;
import com.saurabhshcs.adtech.microservices.designpattern.saga.common.OrchestratorState;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import static com.saurabhshcs.adtech.microservices.designpattern.saga.common.LogMessage.*;

@Slf4j
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
        log.info(ORCHESTRATION_STARTED.getMessage(), campaignId);
        try {
            if (!budgetOk) throw new IllegalStateException(BUDGET_FAIL);
            state = OrchestratorState.BUDGET_VALIDATED;
            log.info(BUDGET_VALIDATED.getMessage(), campaignId);

            if (!inventoryOk) throw new IllegalStateException(INVENTORY_FAIL);
            state = OrchestratorState.INVENTORY_RESERVED;
            log.info(INVENTORY_RESERVED.getMessage(), campaignId);

            if (!scheduleOk) throw new IllegalStateException(SCHEDULE_FAIL);
            state = OrchestratorState.SCHEDULED;
            log.info(CAMPAIGN_SCHEDULED.getMessage(), campaignId);

            state = OrchestratorState.COMPLETED;
            log.info(ORCHESTRATION_COMPLETED.getMessage(), campaignId);
            return state;
        } catch (Exception e) {
            log.error(ORCHESTRATION_FAILED.getMessage(), campaignId, e.getMessage(), e);
            compensate(campaignId);
            state = OrchestratorState.FAILED;
            return state;
        }
    }

    private void compensate(UUID campaignId) {
        log.warn(COMPENSATION_TRIGGERED.getMessage(), campaignId);
        // In production: cancel inventory, release budget, unschedule
        // Kept minimal for training lab
    }

    public OrchestratorState currentState() {
        return state;
    }
}
