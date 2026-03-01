package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD unit tests for {@link Account} domain aggregate.
 * 3 positive and 3 negative scenarios.
 */
class AccountDomainTest {

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void transfer_reducesBalanceByAmount() {
        Account account = Account.open("OWN-T1", "Alice", new BigDecimal("1000"));
        account.drainPendingEvents(); // clear open event

        account.transfer(UUID.randomUUID(), new BigDecimal("300"), "Rent payment");

        assertThat(account.getBalance()).isEqualByComparingTo("700");
    }

    @Test
    void drainPendingEvents_clearsPendingEventsAfterDrain() {
        Account account = Account.open("OWN-T2", "Bob", new BigDecimal("500"));
        account.deposit(new BigDecimal("100"), "Bonus");

        List<?> events = account.drainPendingEvents();
        assertThat(events).hasSize(2); // AccountOpened + MoneyDeposited

        // Second drain should return empty
        assertThat(account.drainPendingEvents()).isEmpty();
    }

    @Test
    void deposit_withPositiveAmount_updatesBalance() {
        Account account = Account.open("OWN-T3", "Carol", new BigDecimal("200"));
        account.drainPendingEvents();

        account.deposit(new BigDecimal("50"), "Refund");

        assertThat(account.getBalance()).isEqualByComparingTo("250");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void deposit_withZeroAmount_throwsIllegalArgumentException() {
        Account account = Account.open("OWN-N1", "Dave", new BigDecimal("500"));

        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO, "Invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void deposit_withNegativeAmount_throwsIllegalArgumentException() {
        Account account = Account.open("OWN-N2", "Eve", new BigDecimal("500"));

        assertThatThrownBy(() -> account.deposit(new BigDecimal("-50"), "Invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transfer_withInsufficientFunds_throwsIllegalStateException() {
        Account account = Account.open("OWN-N3", "Frank", new BigDecimal("100"));

        assertThatThrownBy(() -> account.transfer(UUID.randomUUID(), new BigDecimal("500"), "Too much"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void withdraw_withZeroAmount_throwsIllegalArgumentException() {
        Account account = Account.open("OWN-N4", "Grace", new BigDecimal("500"));

        assertThatThrownBy(() -> account.withdraw(BigDecimal.ZERO, "Invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withdraw_withInsufficientFunds_throwsIllegalStateException() {
        Account account = Account.open("OWN-N5", "Henry", new BigDecimal("100"));

        assertThatThrownBy(() -> account.withdraw(new BigDecimal("500"), "Too much"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
    }
}
