package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.domain;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Getter
public class Account {
    private UUID accountId;
    private String ownerId;
    private String ownerName;
    private BigDecimal balance;
    private boolean active;
    private final List<AccountEvent> pendingEvents = new ArrayList<>();
    private Account() {}
    public static Account open(String ownerId, String ownerName, BigDecimal initialDeposit) {
        Account account = new Account();
        AccountEvent event = new AccountEvent.AccountOpenedEvent(UUID.randomUUID(), ownerId, ownerName, initialDeposit, Instant.now());
        account.apply(event);
        account.pendingEvents.add(event);
        return account;
    }
    public void deposit(BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Deposit must be positive");
        AccountEvent event = new AccountEvent.MoneyDepositedEvent(accountId, amount, description, Instant.now());
        apply(event); pendingEvents.add(event);
    }
    public void withdraw(BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Withdrawal must be positive");
        if (balance.compareTo(amount) < 0) throw new IllegalStateException("Insufficient funds");
        AccountEvent event = new AccountEvent.MoneyWithdrawnEvent(accountId, amount, description, Instant.now());
        apply(event); pendingEvents.add(event);
    }
    public void transfer(UUID targetAccountId, BigDecimal amount, String description) {
        if (balance.compareTo(amount) < 0) throw new IllegalStateException("Insufficient funds for transfer");
        AccountEvent event = new AccountEvent.MoneyTransferredEvent(accountId, targetAccountId, amount, description, Instant.now());
        apply(event); pendingEvents.add(event);
    }
    private void apply(AccountEvent event) {
        switch (event) {
            case AccountEvent.AccountOpenedEvent e -> { this.accountId = e.accountId(); this.ownerId = e.ownerId(); this.ownerName = e.ownerName(); this.balance = e.initialDeposit(); this.active = true; }
            case AccountEvent.MoneyDepositedEvent e -> this.balance = this.balance.add(e.amount());
            case AccountEvent.MoneyWithdrawnEvent e -> this.balance = this.balance.subtract(e.amount());
            case AccountEvent.MoneyTransferredEvent e -> this.balance = this.balance.subtract(e.amount());
        }
    }
    public List<AccountEvent> drainPendingEvents() {
        List<AccountEvent> events = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
