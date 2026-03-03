package com.saurabhshcs.adtech.microservices.designpattern.saga.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link OrchestratorState}.
 * 3 positive and 3 negative scenarios.
 */
class OrchestratorStateTest {

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void completedState_isTerminal() {
        assertThat(OrchestratorState.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void failedState_isTerminal() {
        assertThat(OrchestratorState.FAILED.isTerminal()).isTrue();
    }

    @Test
    void completedState_isSuccess() {
        assertThat(OrchestratorState.COMPLETED.isSuccess()).isTrue();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void failedState_isNotSuccess() {
        assertThat(OrchestratorState.FAILED.isSuccess()).isFalse();
    }

    @Test
    void intermediateStates_areNotTerminal() {
        assertThat(OrchestratorState.STARTED.isTerminal()).isFalse();
        assertThat(OrchestratorState.BUDGET_VALIDATED.isTerminal()).isFalse();
        assertThat(OrchestratorState.INVENTORY_RESERVED.isTerminal()).isFalse();
        assertThat(OrchestratorState.SCHEDULED.isTerminal()).isFalse();
    }

    @Test
    void allStates_haveNonBlankDescription() {
        for (OrchestratorState state : OrchestratorState.values()) {
            assertThat(state.getDescription())
                    .as("Description for %s", state)
                    .isNotBlank();
        }
    }
}
