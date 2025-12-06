package com.saurabhshcs.adtech.microservices.designpattern.saga;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saurabhshcs.adtech.microservices.designpattern.saga.common.OrchestratorState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static com.saurabhshcs.adtech.microservices.designpattern.saga.common.LogMessage.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignOrchestratorTest {

    private static final String CAMPAIGN_ORCHESTRATION_FAILED = "Campaign orchestration failed";
    private static final String COMPENSATING_TRANSACTION = "Compensating transaction";
    private static final String TARGET = "{}";
    private static final String REPLACEMENT = "";
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(CampaignOrchestrator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

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

    @Test
    void shouldLogAllStagesOnSuccess() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        UUID campaignId = UUID.randomUUID();

        orch.execute(campaignId, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);

        List<ILoggingEvent> logEvents = logAppender.list;

        // Verify all expected log messages are present
        assertTrue(containsLogMessage(logEvents, ORCHESTRATION_STARTED.getMessage()));
        assertTrue(containsLogMessage(logEvents, BUDGET_VALIDATED.getMessage()));
        assertTrue(containsLogMessage(logEvents, INVENTORY_RESERVED.getMessage()));
        assertTrue(containsLogMessage(logEvents, CAMPAIGN_SCHEDULED.getMessage()));
        assertTrue(containsLogMessage(logEvents, ORCHESTRATION_COMPLETED.getMessage()));

        // Verify no error or warning logs
        assertFalse(logEvents.stream().anyMatch(e -> e.getLevel() == Level.ERROR));
        assertFalse(logEvents.stream().anyMatch(e -> e.getLevel() == Level.WARN));
    }

    @Test
    void shouldLogErrorAndCompensationOnFailure() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        UUID campaignId = UUID.randomUUID();

        orch.execute(campaignId, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);

        List<ILoggingEvent> logEvents = logAppender.list;

        // Verify error logging occurred
        assertTrue(logEvents.stream().anyMatch(e ->
            e.getLevel() == Level.ERROR &&
            e.getFormattedMessage().contains(CAMPAIGN_ORCHESTRATION_FAILED)));

        // Verify compensation warning
        assertTrue(logEvents.stream().anyMatch(e ->
            e.getLevel() == Level.WARN &&
            e.getFormattedMessage().contains(COMPENSATING_TRANSACTION)));
    }

    @Test
    void shouldLogBudgetValidationFailure() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        UUID campaignId = UUID.randomUUID();

        orch.execute(campaignId, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);

        List<ILoggingEvent> logEvents = logAppender.list;

        // Should have started but not validated budget
        assertTrue(containsLogMessage(logEvents, ORCHESTRATION_STARTED.getMessage()));
        assertFalse(containsLogMessage(logEvents, BUDGET_VALIDATED.getMessage()));

        // Should have error and compensation
        assertTrue(containsLogMessage(logEvents, ORCHESTRATION_FAILED.getMessage()));
        assertTrue(containsLogMessage(logEvents, COMPENSATION_TRIGGERED.getMessage()));
    }

    @Test
    void shouldLogScheduleFailureAfterInventory() {
        CampaignOrchestrator orch = new CampaignOrchestrator();
        UUID campaignId = UUID.randomUUID();

        orch.execute(campaignId, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);

        List<ILoggingEvent> logEvents = logAppender.list;

        // Should have validated budget and reserved inventory
        assertTrue(containsLogMessage(logEvents, BUDGET_VALIDATED.getMessage()));
        assertTrue(containsLogMessage(logEvents, INVENTORY_RESERVED.getMessage()));

        // Should NOT have scheduled
        assertFalse(containsLogMessage(logEvents, CAMPAIGN_SCHEDULED.getMessage()));

        // Should have failed with compensation
        assertTrue(containsLogMessage(logEvents, ORCHESTRATION_FAILED.getMessage()));
        assertTrue(containsLogMessage(logEvents, COMPENSATION_TRIGGERED.getMessage()));
    }

    private Boolean containsLogMessage(List<ILoggingEvent> logEvents, String messageTemplate) {
        String normalizedTemplate = messageTemplate.replace(TARGET, REPLACEMENT).trim();
        return logEvents.stream()
                .anyMatch(e -> e.getMessage().replace(TARGET, REPLACEMENT).trim().equals(normalizedTemplate));
    }
}