package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.api;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/banking/queries") @RequiredArgsConstructor
public class AccountQueryController {
    private final AccountQueryService queryService;
    @GetMapping("/accounts") public ResponseEntity<?> getAllAccounts() { return ResponseEntity.ok(queryService.getAllAccounts()); }
    @GetMapping("/accounts/{id}") public ResponseEntity<?> getAccount(@PathVariable UUID id) { return queryService.getAccount(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build()); }
    @GetMapping("/accounts/{id}/transactions") public ResponseEntity<?> getTransactions(@PathVariable UUID id) { return ResponseEntity.ok(queryService.getTransactionHistory(id)); }
}
