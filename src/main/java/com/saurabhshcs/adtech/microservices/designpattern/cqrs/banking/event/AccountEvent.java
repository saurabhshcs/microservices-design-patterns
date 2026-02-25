package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public sealed interface AccountEvent permits
        AccountEvent.AccountOpenedEvent,
        AccountEvent.MoneyDepositedEvent,
        AccountEvent.MoneyWithdrawnEvent,
        AccountEvent.MoneyTransferredEvent {
    UUID accountId();
    Instant occurredAt();
    record AccountOpenedEvent(UUID accountId, String ownerId, String ownerName, BigDecimal initialDeposit, Instant occurredAt) implements AccountEvent {}
    record MoneyDepositedEvent(UUID accountId, BigDecimal amount, String description, Instant occurredAt) implements AccountEvent {}
    record MoneyWithdrawnEvent(UUID accountId, BigDecimal amount, String description, Instant occurredAt) implements AccountEvent {}
    record MoneyTransferredEvent(UUID accountId, UUID targetAccountId, BigDecimal amount, String description, Instant occurredAt) implements AccountEvent {}
}
