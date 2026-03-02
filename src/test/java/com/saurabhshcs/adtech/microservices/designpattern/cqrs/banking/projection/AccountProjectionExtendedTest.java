package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.projection;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository.InMemoryAccountReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for uncovered {@link AccountProjection} event handlers.
 * 3 positive and 3 negative scenarios.
 */
class AccountProjectionExtendedTest {

    private InMemoryAccountReadRepository readRepo;
    private AccountProjection projection;

    @BeforeEach
    void setUp() {
        readRepo = new InMemoryAccountReadRepository();
        projection = new AccountProjection(readRepo);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void onMoneyTransferredEvent_reducesBalanceInReadModel() {
        UUID accountId = UUID.randomUUID();
        // First open the account so the projection can find it
        projection.on(new AccountEvent.AccountOpenedEvent(
                accountId, "OWN-P1", "Alice", new BigDecimal("1000"), Instant.now()));

        projection.on(new AccountEvent.MoneyTransferredEvent(
                accountId, UUID.randomUUID(), new BigDecimal("300"), "Rent", Instant.now()));

        assertThat(readRepo.findAccountById(accountId).orElseThrow().getBalance())
                .isEqualByComparingTo("700");
    }

    @Test
    void onMoneyTransferredEvent_addsTransferOutTransaction() {
        UUID accountId = UUID.randomUUID();
        projection.on(new AccountEvent.AccountOpenedEvent(
                accountId, "OWN-P2", "Bob", new BigDecimal("500"), Instant.now()));

        projection.on(new AccountEvent.MoneyTransferredEvent(
                accountId, UUID.randomUUID(), new BigDecimal("100"), "Gift", Instant.now()));

        List<?> txns = readRepo.findTransactionsByAccountId(accountId);
        assertThat(txns).hasSizeGreaterThanOrEqualTo(2); // open + transfer
        assertThat(txns).isNotEmpty();
    }

    @Test
    void onMoneyTransferredEvent_incrementsTransactionCount() {
        UUID accountId = UUID.randomUUID();
        projection.on(new AccountEvent.AccountOpenedEvent(
                accountId, "OWN-P3", "Carol", new BigDecimal("800"), Instant.now()));
        int countBefore = readRepo.findAccountById(accountId).orElseThrow().getTransactionCount();

        projection.on(new AccountEvent.MoneyTransferredEvent(
                accountId, UUID.randomUUID(), new BigDecimal("50"), "Coffee", Instant.now()));

        int countAfter = readRepo.findAccountById(accountId).orElseThrow().getTransactionCount();
        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void onMoneyTransferredEvent_forUnknownAccount_doesNotThrow() {
        UUID unknownId = UUID.randomUUID();
        // No AccountOpened event — projection handles missing account gracefully
        projection.on(new AccountEvent.MoneyTransferredEvent(
                unknownId, UUID.randomUUID(), new BigDecimal("100"), "Ghost", Instant.now()));
        // Transaction is still recorded even if account view is missing
        assertThat(readRepo.findTransactionsByAccountId(unknownId)).hasSize(1);
    }

    @Test
    void onMoneyDepositedEvent_forUnknownAccount_recordsTransactionOnly() {
        UUID unknownId = UUID.randomUUID();
        projection.on(new AccountEvent.MoneyDepositedEvent(
                unknownId, new BigDecimal("200"), "Mystery", Instant.now()));

        assertThat(readRepo.findTransactionsByAccountId(unknownId)).hasSize(1);
    }

    @Test
    void onMoneyWithdrawnEvent_forUnknownAccount_recordsTransactionOnly() {
        UUID unknownId = UUID.randomUUID();
        projection.on(new AccountEvent.MoneyWithdrawnEvent(
                unknownId, new BigDecimal("50"), "ATM", Instant.now()));

        assertThat(readRepo.findTransactionsByAccountId(unknownId)).hasSize(1);
    }
}
