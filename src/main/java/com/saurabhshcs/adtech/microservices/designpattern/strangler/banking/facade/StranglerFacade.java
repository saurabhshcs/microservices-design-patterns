package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.facade;

import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.config.FeatureToggle;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.legacy.LegacyAccountService;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.model.AccountSummary;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.modern.ModernAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Strangler Fig Facade — Banking / Account Management.
 *
 * <p>This facade sits in front of both the legacy monolith and the modern microservice.
 * Feature toggles control which implementation handles each operation, allowing
 * gradual, zero-downtime migration. Once all toggles are flipped to modern, the
 * legacy code path is removed — the monolith has been "strangled".</p>
 *
 * <pre>
 *   Client
 *     │
 *     ▼
 *   StranglerFacade  ──[toggle=LEGACY]──► LegacyAccountService (monolith)
 *                    ──[toggle=MODERN]──► ModernAccountService (microservice)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StranglerFacade {

    private final LegacyAccountService legacyService;
    private final ModernAccountService modernService;
    private final FeatureToggle toggle;

    public AccountSummary createAccount(String ownerId, String ownerName, BigDecimal balance) {
        if (toggle.isUseModernForCreate()) {
            log.info("[STRANGLER] createAccount → MODERN");
            return modernService.createAccount(ownerId, ownerName, balance);
        }
        log.info("[STRANGLER] createAccount → LEGACY");
        return legacyService.createAccount(ownerId, ownerName, balance);
    }

    public Optional<AccountSummary> findAccount(UUID accountId) {
        if (toggle.isUseModernForRead()) {
            log.info("[STRANGLER] findAccount → MODERN");
            return modernService.findAccount(accountId);
        }
        log.info("[STRANGLER] findAccount → LEGACY");
        return legacyService.findAccount(accountId);
    }

    public Optional<AccountSummary> updateBalance(UUID accountId, BigDecimal newBalance) {
        if (toggle.isUseModernForUpdate()) {
            log.info("[STRANGLER] updateBalance → MODERN");
            return modernService.updateBalance(accountId, newBalance);
        }
        log.info("[STRANGLER] updateBalance → LEGACY");
        return legacyService.updateBalance(accountId, newBalance);
    }
}
