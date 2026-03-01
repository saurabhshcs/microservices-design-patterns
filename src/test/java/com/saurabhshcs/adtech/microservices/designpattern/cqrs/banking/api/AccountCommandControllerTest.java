package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.api;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command.AccountCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link AccountCommandController}.
 * 3 positive and 3 negative scenarios.
 */
class AccountCommandControllerTest {

    private AccountCommandService commandService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        commandService = mock(AccountCommandService.class);
        client = WebTestClient.bindToController(new AccountCommandController(commandService)).build();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void createAccount_returns201WithAccountId() {
        UUID accountId = UUID.randomUUID();
        when(commandService.handle(any(AccountCommand.CreateAccountCommand.class))).thenReturn(accountId);

        client.post().uri("/api/v1/banking/commands/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("ownerId", "OWN-1", "ownerName", "Alice", "initialDeposit", "1000"))
                .exchange()
                .expectStatus().isEqualTo(201)
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(accountId.toString());
    }

    @Test
    void deposit_returns204NoContent() {
        UUID accountId = UUID.randomUUID();
        when(commandService.handle(any(AccountCommand.DepositMoneyCommand.class))).thenReturn(accountId);

        client.post().uri("/api/v1/banking/commands/accounts/{id}/deposit", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("amount", "250", "description", "Salary"))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void withdraw_returns204NoContent() {
        UUID accountId = UUID.randomUUID();
        when(commandService.handle(any(AccountCommand.WithdrawMoneyCommand.class))).thenReturn(accountId);

        client.post().uri("/api/v1/banking/commands/accounts/{id}/withdraw", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("amount", "100", "description", "ATM"))
                .exchange()
                .expectStatus().isNoContent();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void createAccount_whenServiceThrows_propagatesError() {
        when(commandService.handle(any(AccountCommand.CreateAccountCommand.class)))
                .thenThrow(new IllegalArgumentException("Invalid data"));

        client.post().uri("/api/v1/banking/commands/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("ownerId", "OWN-X", "ownerName", "Bad", "initialDeposit", "-100"))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void deposit_whenServiceThrows_propagatesError() {
        UUID accountId = UUID.randomUUID();
        when(commandService.handle(any(AccountCommand.DepositMoneyCommand.class)))
                .thenThrow(new IllegalArgumentException("Account not found"));

        client.post().uri("/api/v1/banking/commands/accounts/{id}/deposit", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("amount", "50", "description", "Test"))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void withdraw_whenServiceThrows_propagatesError() {
        UUID accountId = UUID.randomUUID();
        when(commandService.handle(any(AccountCommand.WithdrawMoneyCommand.class)))
                .thenThrow(new IllegalStateException("Insufficient funds"));

        client.post().uri("/api/v1/banking/commands/accounts/{id}/withdraw", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("amount", "9999", "description", "Overdraft"))
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
