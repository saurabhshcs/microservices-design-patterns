package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.api;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command.AccountCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/banking/commands") @RequiredArgsConstructor
public class AccountCommandController {
    private final AccountCommandService commandService;
    @PostMapping("/accounts")
    public ResponseEntity<Map<String, UUID>> createAccount(@RequestBody Map<String, Object> req) {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand((String)req.get("ownerId"),(String)req.get("ownerName"),new BigDecimal(req.get("initialDeposit").toString())));
        return ResponseEntity.status(201).body(Map.of("accountId", id));
    }
    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<Void> deposit(@PathVariable UUID id, @RequestBody Map<String, Object> req) {
        commandService.handle(new AccountCommand.DepositMoneyCommand(id,new BigDecimal(req.get("amount").toString()),(String)req.get("description")));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id, @RequestBody Map<String, Object> req) {
        commandService.handle(new AccountCommand.WithdrawMoneyCommand(id,new BigDecimal(req.get("amount").toString()),(String)req.get("description")));
        return ResponseEntity.noContent().build();
    }
}
