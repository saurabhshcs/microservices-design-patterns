package com.saurabhshcs.adtech.microservices.designpattern.saga.orchestrator;

import com.saurabhshcs.adtech.microservices.designpattern.saga.common.LogMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TDD tests for {@link UserServiceOrchestrator} and {@link LogMessage}.
 * 3 positive and 3 negative scenarios.
 */
class UserServiceOrchestratorTest {

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void mainMethod_buildsUserModelWithoutException() {
        assertThatCode(() -> UserServiceOrchestrator.main(new String[]{}))
                .doesNotThrowAnyException();
    }

    @Test
    void mainMethod_withNullArgs_doesNotThrow() {
        assertThatCode(() -> UserServiceOrchestrator.main(null))
                .doesNotThrowAnyException();
    }

    @Test
    void logMessage_toString_returnsMessageTemplate() {
        // Covers the overridden toString() method in LogMessage (1 missed line)
        String s = LogMessage.ORCHESTRATION_STARTED.toString();
        assertThat(s).contains("{}");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void logMessage_getMessage_returnsNonEmptyTemplate() {
        for (LogMessage msg : LogMessage.values()) {
            assertThat(msg.getMessage()).isNotBlank();
        }
    }

    @Test
    void logMessage_compensationTriggered_hasCorrectFormat() {
        assertThat(LogMessage.COMPENSATION_TRIGGERED.getMessage()).contains("{}");
        assertThat(LogMessage.COMPENSATION_TRIGGERED.toString()).isEqualTo(
                LogMessage.COMPENSATION_TRIGGERED.getMessage());
    }

    @Test
    void mainMethod_withExtraArgs_doesNotThrow() {
        assertThatCode(() -> UserServiceOrchestrator.main(new String[]{"ignored"}))
                .doesNotThrowAnyException();
    }
}
