package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.eventstore;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link InMemoryEventStore}.
 * 3 positive and 3 negative scenarios.
 */
class InMemoryEventStoreTest {

    private InMemoryEventStore eventStore;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        accountId = UUID.randomUUID();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void append_andLoadEvents_returnsEventsInOrder() {
        AccountEvent open = new AccountEvent.AccountOpenedEvent(
                accountId, "OWN-1", "Alice", new BigDecimal("1000"), Instant.now());
        AccountEvent deposit = new AccountEvent.MoneyDepositedEvent(
                accountId, new BigDecimal("500"), "Salary", Instant.now().plusMillis(10));

        eventStore.append(accountId, List.of(open, deposit));

        List<AccountEvent> events = eventStore.loadEvents(accountId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AccountEvent.AccountOpenedEvent.class);
        assertThat(events.get(1)).isInstanceOf(AccountEvent.MoneyDepositedEvent.class);
    }

    @Test
    void loadEventsSince_returnsOnlyEventsAfterCutoff() {
        Instant past = Instant.now().minusSeconds(60);
        Instant cutoff = Instant.now();

        AccountEvent old = new AccountEvent.MoneyDepositedEvent(
                accountId, new BigDecimal("100"), "Old", past);
        AccountEvent recent = new AccountEvent.MoneyDepositedEvent(
                accountId, new BigDecimal("200"), "Recent", cutoff.plusMillis(1));

        eventStore.append(accountId, List.of(old, recent));

        List<AccountEvent> since = eventStore.loadEventsSince(accountId, cutoff);
        assertThat(since).hasSize(1);
        assertThat(((AccountEvent.MoneyDepositedEvent) since.get(0)).description()).isEqualTo("Recent");
    }

    @Test
    void loadAllEvents_aggregatesEventsAcrossMultipleAccounts() {
        UUID other = UUID.randomUUID();
        eventStore.append(accountId, List.of(new AccountEvent.AccountOpenedEvent(
                accountId, "OWN-A", "Alice", new BigDecimal("500"), Instant.now())));
        eventStore.append(other, List.of(new AccountEvent.AccountOpenedEvent(
                other, "OWN-B", "Bob", new BigDecimal("750"), Instant.now().plusMillis(5))));

        List<AccountEvent> all = eventStore.loadAllEvents();
        assertThat(all).hasSize(2);
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void loadEvents_forUnknownAggregate_returnsEmptyList() {
        List<AccountEvent> events = eventStore.loadEvents(UUID.randomUUID());
        assertThat(events).isEmpty();
    }

    @Test
    void append_withNullOrEmptyList_storesNothing() {
        eventStore.append(accountId, null);
        eventStore.append(accountId, List.of());

        assertThat(eventStore.loadEvents(accountId)).isEmpty();
    }

    @Test
    void loadEventsSince_withFutureCutoff_returnsEmpty() {
        eventStore.append(accountId, List.of(new AccountEvent.MoneyDepositedEvent(
                accountId, new BigDecimal("100"), "Past", Instant.now().minusSeconds(10))));

        List<AccountEvent> since = eventStore.loadEventsSince(accountId, Instant.now().plusSeconds(60));
        assertThat(since).isEmpty();
    }
}
