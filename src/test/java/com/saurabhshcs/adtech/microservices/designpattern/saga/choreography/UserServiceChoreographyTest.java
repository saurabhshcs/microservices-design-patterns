package com.saurabhshcs.adtech.microservices.designpattern.saga.choreography;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TDD tests for {@link UserServiceChoreography}.
 * 3 positive and 3 negative scenarios.
 */
class UserServiceChoreographyTest {

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void mainMethod_doesNotThrowException() {
        assertThatCode(() -> UserServiceChoreography.main(new String[]{}))
                .doesNotThrowAnyException();
    }

    @Test
    void mainMethod_withNullArgs_doesNotThrow() {
        assertThatCode(() -> UserServiceChoreography.main(null))
                .doesNotThrowAnyException();
    }

    @Test
    void instantiation_succeeds() {
        assertThatCode(UserServiceChoreography::new).doesNotThrowAnyException();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void mainMethod_withEmptyArgs_doesNotThrow() {
        assertThatCode(() -> UserServiceChoreography.main(new String[0]))
                .doesNotThrowAnyException();
    }

    @Test
    void mainMethod_withExtraArgs_doesNotThrow() {
        assertThatCode(() -> UserServiceChoreography.main(new String[]{"arg1", "arg2"}))
                .doesNotThrowAnyException();
    }

    @Test
    void class_isNotAbstract() {
        assertThatCode(() -> new UserServiceChoreography()).doesNotThrowAnyException();
    }
}
