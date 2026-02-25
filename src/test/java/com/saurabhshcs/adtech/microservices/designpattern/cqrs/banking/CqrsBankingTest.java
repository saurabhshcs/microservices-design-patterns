package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command.AccountCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.readmodel.AccountView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountCommandService;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.service.AccountQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
class CqrsBankingTest {
    @Autowired AccountCommandService commandService;
    @Autowired AccountQueryService queryService;
    @Test void createAccount_populatesReadModel() {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand("OWN-1","Alice",new BigDecimal("1000")));
        AccountView view = queryService.getAccount(id).orElseThrow();
        assertThat(view.getOwnerName()).isEqualTo("Alice");
        assertThat(view.getBalance()).isEqualByComparingTo("1000");
    }
    @Test void deposit_updatesReadModelBalance() {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand("OWN-2","Bob",new BigDecimal("500")));
        commandService.handle(new AccountCommand.DepositMoneyCommand(id,new BigDecimal("250"),"Salary"));
        assertThat(queryService.getAccount(id).orElseThrow().getBalance()).isEqualByComparingTo("750");
    }
    @Test void withdraw_updatesReadModelBalance() {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand("OWN-3","Carol",new BigDecimal("1000")));
        commandService.handle(new AccountCommand.WithdrawMoneyCommand(id,new BigDecimal("200"),"ATM"));
        assertThat(queryService.getAccount(id).orElseThrow().getBalance()).isEqualByComparingTo("800");
    }
    @Test void transactionHistory_recordsAllEvents() {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand("OWN-4","Dave",new BigDecimal("1000")));
        commandService.handle(new AccountCommand.DepositMoneyCommand(id,new BigDecimal("500"),"Bonus"));
        commandService.handle(new AccountCommand.WithdrawMoneyCommand(id,new BigDecimal("100"),"Bill"));
        assertThat(queryService.getTransactionHistory(id)).hasSize(3);
    }
    @Test void queryAndCommandModelsAreIndependent() {
        UUID id = commandService.handle(new AccountCommand.CreateAccountCommand("OWN-5","Eve",new BigDecimal("2000")));
        assertThat(queryService.getAllAccounts()).anyMatch(a -> a.getAccountId().equals(id));
    }
}
