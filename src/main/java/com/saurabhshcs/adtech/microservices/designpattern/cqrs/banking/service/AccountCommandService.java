package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command.AccountCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.domain.Account;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository.InMemoryAccountWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Slf4j @Service @RequiredArgsConstructor
public class AccountCommandService {
    private final InMemoryAccountWriteRepository writeRepository;
    private final ApplicationEventPublisher eventPublisher;
    public UUID handle(AccountCommand command) {
        return switch (command) {
            case AccountCommand.CreateAccountCommand c -> {
                Account account = Account.open(c.ownerId(), c.ownerName(), c.initialDeposit());
                writeRepository.save(account); publishEvents(account);
                yield account.getAccountId();
            }
            case AccountCommand.DepositMoneyCommand c -> {
                Account account = loadAccount(c.accountId());
                account.deposit(c.amount(), c.description());
                writeRepository.save(account); publishEvents(account);
                yield c.accountId();
            }
            case AccountCommand.WithdrawMoneyCommand c -> {
                Account account = loadAccount(c.accountId());
                account.withdraw(c.amount(), c.description());
                writeRepository.save(account); publishEvents(account);
                yield c.accountId();
            }
            case AccountCommand.TransferMoneyCommand c -> {
                Account account = loadAccount(c.sourceAccountId());
                account.transfer(c.targetAccountId(), c.amount(), c.description());
                writeRepository.save(account); publishEvents(account);
                yield c.sourceAccountId();
            }
        };
    }
    private Account loadAccount(UUID id) { return writeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found: " + id)); }
    private void publishEvents(Account account) { account.drainPendingEvents().forEach(e -> { log.info("Publishing: {}", e.getClass().getSimpleName()); eventPublisher.publishEvent(e); }); }
}
