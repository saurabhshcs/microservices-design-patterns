# CQRS Pattern - Banking Account Management

## Overview

**CQRS (Command Query Responsibility Segregation)** separates the operations that modify state (commands) from the operations that read state (queries). Instead of a single model serving both reads and writes, CQRS maintains two distinct models optimised for their respective purposes.

## Problem Statement

Traditional CRUD architectures use the same data model for both reading and writing. This creates problems:
- **Read/Write contention**: Optimizing for queries (joins, denormalization) conflicts with write integrity
- **Scaling limitations**: Read and write loads often have different characteristics
- **Complexity**: Single model tries to serve all consumers (UI, reports, analytics)

## Solution: Separate Command and Query Models

```
                    ┌─── COMMAND SIDE ───┐
Client ─── writes → │ AccountCommandController │
                    │ AccountCommandService    │──→ Account (aggregate)
                    │ WriteRepository          │──→ EventPublisher ──→
                    └─────────────────────────┘                    │
                                                                   ▼
                    ┌─── EVENT BRIDGE ───────────────────────────┐
                    │ AccountProjection (@EventListener)          │
                    │ Updates AccountView in ReadRepository       │
                    └─────────────────────────────────────────────┘
                                                                   │
                    ┌─── QUERY SIDE ─────┐                        │
Client ── reads ──→ │ AccountQueryController  │←── ReadRepository ←┘
                    │ AccountQueryService     │
                    └─────────────────────────┘
```

## Architecture

### Command Side

#### Sealed AccountCommand Interface (Java 21)
```java
public sealed interface AccountCommand permits
        CreateAccountCommand, DepositMoneyCommand,
        WithdrawMoneyCommand, TransferMoneyCommand {
    record CreateAccountCommand(String ownerId, String ownerName, BigDecimal initialDeposit) implements AccountCommand {}
    record DepositMoneyCommand(UUID accountId, BigDecimal amount, String description) implements AccountCommand {}
    // ...
}
```

#### Account Aggregate (Write Model)
- Encapsulates business rules (positive amounts, sufficient balance)
- Produces `AccountEvent` objects from commands
- Collects pending events via `drainPendingEvents()`

#### AccountCommandService
```java
public UUID handle(AccountCommand command) {
    return switch (command) {
        case CreateAccountCommand c -> { /* open account, publish events */ }
        case DepositMoneyCommand c  -> { /* deposit, publish events */ }
        // ...
    };
}
```

### Event Bridge

#### AccountProjection
Listens to domain events and maintains the read model:
```java
@EventListener
public void on(AccountEvent.MoneyDepositedEvent event) {
    readRepository.findAccountById(event.accountId()).ifPresent(view -> {
        view.setBalance(view.getBalance().add(event.amount()));
        view.setTransactionCount(view.getTransactionCount() + 1);
        readRepository.saveAccountView(view);
    });
    readRepository.addTransaction(/* TransactionView */);
}
```

### Query Side

#### AccountView (Read Model)
Denormalized, query-optimized view:
```java
AccountView {
    UUID accountId;
    String ownerName;
    BigDecimal balance;
    int transactionCount;
}
```

## REST API

### Command Endpoints

```bash
# Create account
POST /api/v1/banking/commands/accounts
{"ownerId":"OWN-1","ownerName":"Alice","initialDeposit":1000.00}

# Deposit money
POST /api/v1/banking/commands/accounts/{id}/deposit
{"amount":500.00,"description":"Salary"}

# Withdraw money
POST /api/v1/banking/commands/accounts/{id}/withdraw
{"amount":200.00,"description":"ATM withdrawal"}
```

### Query Endpoints

```bash
# Get all accounts
GET /api/v1/banking/queries/accounts

# Get specific account
GET /api/v1/banking/queries/accounts/{id}

# Get transaction history
GET /api/v1/banking/queries/accounts/{id}/transactions
```

## Key Principles

1. **Commands never return data** — they return only the ID
2. **Queries never modify state** — they only read from the read model
3. **Eventual consistency** — read model updates asynchronously via events
4. **Separate repositories** — InMemoryAccountWriteRepository vs InMemoryAccountReadRepository

## Trade-offs

### Advantages
- ✅ Read and write models optimized independently
- ✅ Read side can be scaled independently
- ✅ Clear separation of concerns
- ✅ Read model can be rebuilt from event history
- ✅ Java 21 sealed interfaces provide type-safe command/event dispatch

### Disadvantages
- ⚠️ Eventual consistency (read model may lag behind)
- ⚠️ More code — two models to maintain
- ⚠️ In-process events don't survive application restart (use Kafka for durability)

## Technology Stack

- **Java 21** - Sealed interfaces, records, pattern-matching switch
- **Spring Boot 3.4.2** - @EventListener as in-process event bus
- **Lombok** - @Data, @Builder, @RequiredArgsConstructor
- **Spring Actuator** - Health checks

## When to Use CQRS

**Use when:**
- Read and write workloads have very different scaling needs
- Complex domain with many different query requirements
- Event sourcing is used (natural fit)
- High read-to-write ratio (social media, reporting)

**Avoid when:**
- Simple CRUD application
- Small team / startup (premature optimization)
- Domain model and read model would be identical
