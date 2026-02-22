package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.projection;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.event.AccountEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.AccountView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.TransactionView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository.InMemoryAccountReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Slf4j @Component @RequiredArgsConstructor
public class AccountProjection {
    private final InMemoryAccountReadRepository readRepository;
    @EventListener
    public void on(AccountEvent.AccountOpenedEvent event) {
        readRepository.saveAccountView(AccountView.builder().accountId(event.accountId()).ownerId(event.ownerId()).ownerName(event.ownerName()).balance(event.initialDeposit()).active(true).transactionCount(0).build());
        readRepository.addTransaction(TransactionView.builder().transactionId(UUID.randomUUID()).accountId(event.accountId()).type("ACCOUNT_OPENED").amount(event.initialDeposit()).description("Initial deposit").occurredAt(event.occurredAt()).build());
        log.info("Projection: AccountOpened for {}", event.accountId());
    }
    @EventListener
    public void on(AccountEvent.MoneyDepositedEvent event) {
        readRepository.findAccountById(event.accountId()).ifPresent(view -> { view.setBalance(view.getBalance().add(event.amount())); view.setTransactionCount(view.getTransactionCount() + 1); readRepository.saveAccountView(view); });
        readRepository.addTransaction(TransactionView.builder().transactionId(UUID.randomUUID()).accountId(event.accountId()).type("DEPOSIT").amount(event.amount()).description(event.description()).occurredAt(event.occurredAt()).build());
    }
    @EventListener
    public void on(AccountEvent.MoneyWithdrawnEvent event) {
        readRepository.findAccountById(event.accountId()).ifPresent(view -> { view.setBalance(view.getBalance().subtract(event.amount())); view.setTransactionCount(view.getTransactionCount() + 1); readRepository.saveAccountView(view); });
        readRepository.addTransaction(TransactionView.builder().transactionId(UUID.randomUUID()).accountId(event.accountId()).type("WITHDRAWAL").amount(event.amount()).description(event.description()).occurredAt(event.occurredAt()).build());
    }
    @EventListener
    public void on(AccountEvent.MoneyTransferredEvent event) {
        readRepository.findAccountById(event.accountId()).ifPresent(view -> { view.setBalance(view.getBalance().subtract(event.amount())); view.setTransactionCount(view.getTransactionCount() + 1); readRepository.saveAccountView(view); });
        readRepository.addTransaction(TransactionView.builder().transactionId(UUID.randomUUID()).accountId(event.accountId()).type("TRANSFER_OUT").amount(event.amount()).description(event.description()).occurredAt(event.occurredAt()).build());
    }
}
