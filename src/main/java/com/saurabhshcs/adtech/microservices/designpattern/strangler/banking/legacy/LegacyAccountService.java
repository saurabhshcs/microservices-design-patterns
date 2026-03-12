package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.legacy;

import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.model.AccountSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulates the existing monolithic account service that is being strangled.
 * In a real migration this would be the old codebase / COBOL system / legacy DB.
 */
@Slf4j
@Service
public class LegacyAccountService {

    private final Map<UUID, AccountSummary> legacyStore = new HashMap<>();

    public AccountSummary createAccount(String ownerId, String ownerName, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        AccountSummary account = new AccountSummary(id, ownerId, ownerName, balance, "LEGACY");
        legacyStore.put(id, account);
        log.info("[LEGACY] Account created: id={}", id);
        return account;
    }

    public Optional<AccountSummary> findAccount(UUID accountId) {
        log.info("[LEGACY] findAccount: id={}", accountId);
        return Optional.ofNullable(legacyStore.get(accountId));
    }

    public Optional<AccountSummary> updateBalance(UUID accountId, BigDecimal newBalance) {
        return findAccount(accountId).map(a -> {
            AccountSummary updated = new AccountSummary(a.accountId(), a.ownerId(),
                    a.ownerName(), newBalance, "LEGACY");
            legacyStore.put(accountId, updated);
            log.info("[LEGACY] Balance updated: id={} balance={}", accountId, newBalance);
            return updated;
        });
    }
}
