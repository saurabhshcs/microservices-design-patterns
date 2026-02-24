package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.AccountView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.TransactionView;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Repository
public class InMemoryAccountReadRepository {
    private final ConcurrentHashMap<UUID, AccountView> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<TransactionView>> transactions = new ConcurrentHashMap<>();
    public void saveAccountView(AccountView view) { accounts.put(view.getAccountId(), view); }
    public Optional<AccountView> findAccountById(UUID id) { return Optional.ofNullable(accounts.get(id)); }
    public List<AccountView> findAllAccounts() { return new ArrayList<>(accounts.values()); }
    public void addTransaction(TransactionView tx) { transactions.computeIfAbsent(tx.getAccountId(), k -> Collections.synchronizedList(new ArrayList<>())).add(tx); }
    public List<TransactionView> findTransactionsByAccountId(UUID accountId) { return transactions.getOrDefault(accountId, Collections.emptyList()); }
}
