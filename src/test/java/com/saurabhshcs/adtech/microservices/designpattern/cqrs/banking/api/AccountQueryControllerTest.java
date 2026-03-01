package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.api;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.AccountView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link AccountQueryController}.
 * 3 positive and 3 negative scenarios.
 */
class AccountQueryControllerTest {

    private AccountQueryService queryService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        queryService = mock(AccountQueryService.class);
        client = WebTestClient.bindToController(new AccountQueryController(queryService)).build();
    }

    // ── Positive scenarios ─────────────────────────────────────────────────

    @Test
    void getAllAccounts_returns200WithList() {
        AccountView view = AccountView.builder()
                .accountId(UUID.randomUUID()).ownerName("Alice")
                .balance(new BigDecimal("1000")).build();
        when(queryService.getAllAccounts()).thenReturn(List.of(view));

        client.get().uri("/api/v1/banking/queries/accounts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].ownerName").isEqualTo("Alice");
    }

    @Test
    void getAccount_whenFound_returns200WithView() {
        UUID id = UUID.randomUUID();
        AccountView view = AccountView.builder()
                .accountId(id).ownerName("Bob").balance(new BigDecimal("500")).build();
        when(queryService.getAccount(id)).thenReturn(Optional.of(view));

        client.get().uri("/api/v1/banking/queries/accounts/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ownerName").isEqualTo("Bob");
    }

    @Test
    void getTransactions_returns200WithList() {
        UUID id = UUID.randomUUID();
        when(queryService.getTransactionHistory(id)).thenReturn(List.of());

        client.get().uri("/api/v1/banking/queries/accounts/{id}/transactions", id)
                .exchange()
                .expectStatus().isOk();
    }

    // ── Negative scenarios ─────────────────────────────────────────────────

    @Test
    void getAccount_whenNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(queryService.getAccount(id)).thenReturn(Optional.empty());

        client.get().uri("/api/v1/banking/queries/accounts/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getAllAccounts_whenEmpty_returnsEmptyList() {
        when(queryService.getAllAccounts()).thenReturn(List.of());

        client.get().uri("/api/v1/banking/queries/accounts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void getTransactions_forUnknownAccount_returnsEmptyList() {
        UUID unknownId = UUID.randomUUID();
        when(queryService.getTransactionHistory(unknownId)).thenReturn(List.of());

        client.get().uri("/api/v1/banking/queries/accounts/{id}/transactions", unknownId)
                .exchange()
                .expectStatus().isOk();
    }
}
