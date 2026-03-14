package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.modern;

import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.model.AccountSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The new microservice implementation gradually replacing the legacy system.
 * Offers the same interface but with cleaner internals and modern persistence.
 */
@Slf4j
@Service
public class ModernAccountService {

    private final Map<UUID, AccountSummary> modernStore = new HashMap<>();

    public AccountSummary createAccount(String ownerId, String ownerName, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        AccountSummary account = new AccountSummary(id, ownerId, ownerName, balance, "MODERN");
        modernStore.put(id, account);
        log.info("[MODERN] Account created: id={}", id);
        return account;
    }

    public Optional<AccountSummary> findAccount(UUID accountId) {
        log.info("[MODERN] findAccount: id={}", accountId);
        return Optional.ofNullable(modernStore.get(accountId));
    }

    public Optional<AccountSummary> updateBalance(UUID accountId, BigDecimal newBalance) {
        return findAccount(accountId).map(a -> {
            AccountSummary updated = new AccountSummary(a.accountId(), a.ownerId(),
                    a.ownerName(), newBalance, "MODERN");
            modernStore.put(accountId, updated);
            log.info("[MODERN] Balance updated: id={} balance={}", accountId, newBalance);
            return updated;
        });
    }
}
