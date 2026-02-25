package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.repository;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.domain.Account;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Repository
public class InMemoryAccountWriteRepository {
    private final ConcurrentHashMap<UUID, Account> store = new ConcurrentHashMap<>();
    public void save(Account account) { store.put(account.getAccountId(), account); }
    public Optional<Account> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
}
