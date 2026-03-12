package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.facade;

import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.config.FeatureToggle;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.legacy.LegacyAccountService;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.model.AccountSummary;
import com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.modern.ModernAccountService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for {@link StranglerFacade}.
 * 3 positive and 3 negative scenarios.
 */
class StranglerFacadeTest {

    private StranglerFacade facadeWith(FeatureToggle toggle) {
        return new StranglerFacade(new LegacyAccountService(), new ModernAccountService(), toggle);
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void createAccount_withModernToggle_routesToModernService() {
        StranglerFacade facade = facadeWith(FeatureToggle.allModern());

        AccountSummary account = facade.createAccount("OWN-M1", "Alice", new BigDecimal("1000"));

        assertThat(account.isFromModern()).isTrue();
        assertThat(account.isFromLegacy()).isFalse();
    }

    @Test
    void findAccount_withModernToggle_returnsModernResult() {
        StranglerFacade facade = facadeWith(FeatureToggle.allModern());
        AccountSummary created = facade.createAccount("OWN-M2", "Bob", new BigDecimal("500"));

        Optional<AccountSummary> found = facade.findAccount(created.accountId());

        assertThat(found).isPresent();
        assertThat(found.get().isFromModern()).isTrue();
    }

    @Test
    void updateBalance_withModernToggle_updatesModernStore() {
        StranglerFacade facade = facadeWith(FeatureToggle.allModern());
        AccountSummary account = facade.createAccount("OWN-M3", "Carol", new BigDecimal("200"));

        Optional<AccountSummary> updated = facade.updateBalance(account.accountId(), new BigDecimal("800"));

        assertThat(updated).isPresent();
        assertThat(updated.get().balance()).isEqualByComparingTo("800");
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void createAccount_withLegacyToggle_routesToLegacyService() {
        StranglerFacade facade = facadeWith(FeatureToggle.allLegacy());

        AccountSummary account = facade.createAccount("OWN-L1", "Dave", new BigDecimal("300"));

        assertThat(account.isFromLegacy()).isTrue();
        assertThat(account.isFromModern()).isFalse();
    }

    @Test
    void findAccount_inModern_whenCreatedInLegacy_returnsEmpty() {
        // Account created via LEGACY path — modern store knows nothing about it
        FeatureToggle toggle = new FeatureToggle(false, true, false); // create=legacy, read=modern
        StranglerFacade facade = facadeWith(toggle);
        AccountSummary legacyAccount = facade.createAccount("OWN-L2", "Eve", new BigDecimal("750"));

        // Read goes to MODERN — which has no record of this account
        Optional<AccountSummary> result = facade.findAccount(legacyAccount.accountId());

        assertThat(result).isEmpty();
    }

    @Test
    void updateBalance_forUnknownAccount_returnsEmpty() {
        StranglerFacade facade = facadeWith(FeatureToggle.allModern());

        Optional<AccountSummary> result = facade.updateBalance(java.util.UUID.randomUUID(), new BigDecimal("999"));

        assertThat(result).isEmpty();
    }
}
