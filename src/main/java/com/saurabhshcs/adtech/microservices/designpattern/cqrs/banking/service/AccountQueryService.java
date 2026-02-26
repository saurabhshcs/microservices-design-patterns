package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.AccountView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.TransactionView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository.InMemoryAccountReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class AccountQueryService {
    private final InMemoryAccountReadRepository readRepository;
    public Optional<AccountView> getAccount(UUID accountId) { return readRepository.findAccountById(accountId); }
    public List<AccountView> getAllAccounts() { return readRepository.findAllAccounts(); }
    public List<TransactionView> getTransactionHistory(UUID accountId) { return readRepository.findTransactionsByAccountId(accountId); }
}
