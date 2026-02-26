package com.saurabhshcs.adtech.microservices.designpattern.cqrs.banking.command;
import java.math.BigDecimal;
import java.util.UUID;
public sealed interface AccountCommand permits
        AccountCommand.CreateAccountCommand,
        AccountCommand.DepositMoneyCommand,
        AccountCommand.WithdrawMoneyCommand,
        AccountCommand.TransferMoneyCommand {
    record CreateAccountCommand(String ownerId, String ownerName, BigDecimal initialDeposit) implements AccountCommand {}
    record DepositMoneyCommand(UUID accountId, BigDecimal amount, String description) implements AccountCommand {}
    record WithdrawMoneyCommand(UUID accountId, BigDecimal amount, String description) implements AccountCommand {}
    record TransferMoneyCommand(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String description) implements AccountCommand {}
}
