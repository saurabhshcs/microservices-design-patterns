package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command.AccountCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository.InMemoryAccountWriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * TDD unit tests covering branches missed in {@link AccountCommandService}.
 * 3 positive and 3 negative scenarios.
 */
class AccountCommandServiceExtendedTest {

    private AccountCommandService service;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        service = new AccountCommandService(new InMemoryAccountWriteRepository(), publisher);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void transferMoney_succeeds_andReturnsSourceAccountId() {
        UUID sourceId = service.handle(new AccountCommand.CreateAccountCommand(
                "OWN-S1", "Alice", new BigDecimal("1000")));
        UUID targetId = UUID.randomUUID(); // transfer decrements source only

        UUID result = service.handle(new AccountCommand.TransferMoneyCommand(
                sourceId, targetId, new BigDecimal("250"), "Shared rent"));

        assertThat(result).isEqualTo(sourceId);
    }

    @Test
    void createAccount_returnsNonNullAccountId() {
        UUID accountId = service.handle(new AccountCommand.CreateAccountCommand(
                "OWN-S2", "Bob", new BigDecimal("500")));
        assertThat(accountId).isNotNull();
    }

    @Test
    void deposit_returnsAccountId() {
        UUID id = service.handle(new AccountCommand.CreateAccountCommand("OWN-S3", "Carol", new BigDecimal("300")));
        UUID result = service.handle(new AccountCommand.DepositMoneyCommand(id, new BigDecimal("100"), "Salary"));
        assertThat(result).isEqualTo(id);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void deposit_onNonExistentAccount_throwsIllegalArgumentException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> service.handle(
                new AccountCommand.DepositMoneyCommand(unknownId, new BigDecimal("50"), "Test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void withdraw_onNonExistentAccount_throwsIllegalArgumentException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> service.handle(
                new AccountCommand.WithdrawMoneyCommand(unknownId, new BigDecimal("50"), "Test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transfer_onNonExistentSourceAccount_throwsIllegalArgumentException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> service.handle(
                new AccountCommand.TransferMoneyCommand(unknownId, UUID.randomUUID(),
                        new BigDecimal("100"), "Test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }
}
