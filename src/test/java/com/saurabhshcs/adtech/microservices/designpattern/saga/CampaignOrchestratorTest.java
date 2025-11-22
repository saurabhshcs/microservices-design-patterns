package com.saurabhshcs.adtech.microservices.designpattern.saga;

import com.saurabhshcs.adtech.microservices.designpattern.saga.common.OrchestratorState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CampaignOrchestratorTest {

    @Test
    void shouldPathCompletes() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        OrchestratorState result = orch.execute(UUID.randomUUID(), Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
        assertEquals(OrchestratorState.COMPLETED, result);
        assertEquals(OrchestratorState.COMPLETED, orch.currentState());
    }

    @Test
    void shouldFailureTriggersCompensationAndFail() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        OrchestratorState result = orch.execute(UUID.randomUUID(), Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);
        assertEquals(OrchestratorState.FAILED, result);
    }
}