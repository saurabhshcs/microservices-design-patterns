# Transactional Outbox Pattern — Telco SIM Activation Platform

## Table of Contents

1. [The Problem: Dual-Write in Distributed Systems](#the-problem)
2. [The Solution: Transactional Outbox](#the-solution)
3. [Telco Domain Scenario](#telco-domain-scenario)
4. [Architecture Overview](#architecture-overview)
5. [Key Components](#key-components)
6. [Data Flow Walkthrough](#data-flow-walkthrough)
7. [Domain Model & State Machines](#domain-model--state-machines)
8. [Code Deep Dive](#code-deep-dive)
9. [Testing Strategy](#testing-strategy)
10. [Production Considerations](#production-considerations)
11. [Comparison: Outbox vs Alternatives](#comparison-outbox-vs-alternatives)
12. [Running the Demo](#running-the-demo)

---

## The Problem

In a telco SIM activation platform, when a subscriber activates a SIM card, **multiple things must happen consistently**:

1. Update the SIM card record in the database (`status = ACTIVE`).
2. Notify the **Billing Service** to start charging the subscriber.
3. Notify **Network Provisioning** to open data/voice channels on the HLR.
4. Send a **Welcome SMS** via the Notification Service.

A naive implementation would use a "dual-write" approach: update the database, then publish a Kafka event. This creates a critical gap:

```
DB commit succeeds  ──►  [CRASH / network timeout]  ──►  Kafka publish never happens
                                                          ↓
                              Billing never starts billing the customer
                              Network never opens the channel
                              Customer calls support — angry
```

The reverse is equally bad: Kafka publish succeeds but the DB rollbacks. Downstream services act on an event that never materialised in the source of truth.

---

## The Solution

The **Transactional Outbox Pattern** eliminates the dual-write problem by:

1. Writing the business entity change **and** an outbox message row to the **same database** in the **same transaction**.
2. Using a separate **relay service** (or CDC tool) to asynchronously read the outbox and publish to the broker.

Because steps (1a) and (1b) share a transaction, they either both commit or both roll back. The relay can be retried safely because it checks the outbox status before publishing.

```
┌─────────────────────────────────────┐
│        Same DB Transaction          │
│  ┌───────────────┐  ┌─────────────┐│
│  │ SIM Card row  │  │ Outbox row  ││
│  │ status=ACTIVE │  │ PENDING     ││
│  └───────────────┘  └─────────────┘│
└─────────────────────────────────────┘
           ↓ committed atomically

  [OutboxRelayService polls later]
           ↓
  Kafka ← publish event ← mark PUBLISHED
```

---

## Telco Domain Scenario

**Company:** TelcoNova — a European mobile network operator
**Domain:** SIM card lifecycle management
**Scale:** ~50,000 SIM state changes per hour at peak (new phone launches, billing cycles)

### Business Events That Must Reach Downstream Systems

| Domain Event | Triggers |
|---|---|
| `SIMRegisteredEvent` | Creates a subscriber record in billing; reserves MSISDN |
| `SIMActivatedEvent` | Opens network channels; starts billing; sends welcome SMS |
| `SIMSuspendedEvent` | Blocks network access; pauses billing; sends SMS alert |
| `SIMReactivatedEvent` | Restores network; resumes billing; sends confirmation SMS |
| `SIMTerminatedEvent` | Terminates billing; releases MSISDN back to number pool |

### Why Outbox Over Direct Kafka Publish?

- A SIM activation during a Kafka cluster outage **must not be lost**. Outbox rows persist in PostgreSQL even if Kafka is down for hours.
- Support teams need an **audit trail** of every event written (the outbox table serves this purpose).
- The relay can be throttled independently from the write path to protect downstream systems.

---

## Architecture Overview

See `transactional-outbox-pattern.puml` for rendered diagrams. Key elements:

```
Operator
   │ ActivateSIMCommand
   ▼
SIMCardCommandService
   ├── SIMCard.activate()            ← domain logic
   ├── SIMCardRepository.save()      ┐
   └── OutboxRepository.save()       ┘ same transaction
                                        │
                                   (committed)
                                        │
                              OutboxRelayService (scheduled)
                                        │
                               MessageBrokerPublisher
                                        │
                                    Kafka topic
                              ┌────────┴────────┐
                         Billing          Network
                         Service        Provisioning
```

---

## Key Components

### Domain Layer

| Class | Responsibility |
|---|---|
| `SIMCard` | Aggregate root. Enforces business rules. Emits domain events. |
| `SIMActivationStatus` | Enum: `PENDING → ACTIVE → SUSPENDED → TERMINATED` |
| `SIMCardEvent` | Sealed interface with five event records. |
| `SIMCardCommand` | Sealed interface with five command records. |

### Infrastructure Layer

| Class | Responsibility |
|---|---|
| `OutboxMessage` | One row in the outbox table. Carries payload + metadata. |
| `OutboxStatus` | Enum: `PENDING`, `PUBLISHED`, `FAILED` |
| `InMemoryOutboxRepository` | Demo/test implementation of `OutboxRepository`. |
| `InMemorySIMCardRepository` | Demo/test implementation of `SIMCardRepository`. |
| `InMemoryMessageBrokerPublisher` | Captures published events in a list for testing. |

### Service Layer

| Class | Responsibility |
|---|---|
| `SIMCardCommandService` | Handles commands; owns the atomic write (SIM + outbox). |
| `SIMCardQueryService` | Reads SIM state and outbox metrics. |
| `OutboxRelayService` | Polls `PENDING` outbox rows; publishes; marks `PUBLISHED`/`FAILED`. |

---

## Data Flow Walkthrough

### Step 1 — Operator Sends Command

```java
commandService.handle(new SIMCardCommand.ActivateSIMCommand(simId));
```

### Step 2 — Aggregate Enforces Domain Rules

```java
// Inside SIMCard.activate()
requireStatus(SIMActivationStatus.PENDING, "activated");   // throws if wrong state
this.status = SIMActivationStatus.ACTIVE;
pendingEvents.add(new SIMCardEvent.SIMActivatedEvent(...));
```

### Step 3 — Atomic Persistence

```java
// Inside SIMCardCommandService.persistAtomically()
simCardRepository.save(sim);           // SIM row committed
List<SIMCardEvent> events = sim.drainPendingEvents();
events.forEach(this::writeOutboxMessage);  // Outbox row committed
```

> In production, annotate `persistAtomically()` with `@Transactional`. Both repositories must share the same `DataSource`.

### Step 4 — Relay Publishes Asynchronously

```java
// Inside OutboxRelayService.relayPendingMessages()
List<OutboxMessage> pending = outboxRepository.findPendingMessages();
for (OutboxMessage msg : pending) {
    try {
        messageBrokerPublisher.publish(msg);
        msg.markPublished();
        outboxRepository.update(msg);
    } catch (Exception ex) {
        msg.markFailed();
        outboxRepository.update(msg);
    }
}
```

### Step 5 — Downstream Consumers React

Each downstream service subscribes to `sim.events` and filters by `eventType`:

- `SIMActivatedEvent` → Billing starts invoice generation, Network opens HLR channel
- `SIMSuspendedEvent` → Network blocks MSISDN, SMS service sends "Your service is suspended" message

---

## Domain Model & State Machines

### SIM Card Lifecycle

```
[Registration]
      │ RegisterSIMCommand
      ▼
   PENDING
      │ ActivateSIMCommand
      ▼
   ACTIVE ◄────────────────────┐
      │                        │ ReactivateSIMCommand
      │ SuspendSIMCommand       │
      ▼                        │
  SUSPENDED ──────────────────►┘
      │
      │ TerminateSIMCommand
      ▼                        (also reachable from ACTIVE)
  TERMINATED
```

### OutboxMessage Lifecycle

```
[Command Service writes]
      │
      ▼
   PENDING
   ╱      ╲
Relay OK  Relay throws
   │            │
   ▼            ▼
PUBLISHED     FAILED ──► (retry on next poll)
```

---

## Code Deep Dive

### Sealed Command Interface (Java 17)

```java
public sealed interface SIMCardCommand
        permits SIMCardCommand.RegisterSIMCommand,
                SIMCardCommand.ActivateSIMCommand,
                SIMCardCommand.SuspendSIMCommand,
                SIMCardCommand.TerminateSIMCommand,
                SIMCardCommand.ReactivateSIMCommand {

    record RegisterSIMCommand(String iccid, String msisdn, String customerId) implements SIMCardCommand {}
    record ActivateSIMCommand(String simId) implements SIMCardCommand {}
    // ...
}
```

**Why sealed?** The switch expression in `SIMCardCommandService.handle()` is exhaustive at compile time. Adding a new command without handling it fails the build.

### Command Handler with Exhaustive Switch

```java
public SIMCard handle(SIMCardCommand command) {
    return switch (command) {
        case SIMCardCommand.RegisterSIMCommand cmd   -> registerSIM(cmd);
        case SIMCardCommand.ActivateSIMCommand cmd   -> activateSIM(cmd);
        case SIMCardCommand.SuspendSIMCommand cmd    -> suspendSIM(cmd);
        case SIMCardCommand.TerminateSIMCommand cmd  -> terminateSIM(cmd);
        case SIMCardCommand.ReactivateSIMCommand cmd -> reactivateSIM(cmd);
    };
}
```

### Atomic Write

```java
private void persistAtomically(SIMCard sim) {
    simCardRepository.save(sim);                      // (1) business data
    List<SIMCardEvent> events = sim.drainPendingEvents();
    events.forEach(this::writeOutboxMessage);          // (2) outbox rows
    // Production: both (1) and (2) in @Transactional boundary
}
```

### Outbox Message Builder

```java
OutboxMessage.builder()
    .messageId(UUID.randomUUID().toString())
    .aggregateId(simId)
    .aggregateType("SIMCard")
    .eventType("SIMActivatedEvent")
    .payload(objectMapper.writeValueAsString(payloadMap))
    .status(OutboxStatus.PENDING)
    .createdAt(Instant.now())
    .build();
```

---

## Testing Strategy

### Test Classes

| Class | Type | Coverage |
|---|---|---|
| `SIMCardCommandServiceTest` | Unit | Command handling, domain rule enforcement, outbox writes |
| `OutboxRelayServiceTest` | Unit | Relay happy path, broker failure, partial failure, idempotency |
| `SIMCardQueryServiceTest` | Unit | findById, findByMsisdn, findByIccid, outbox counts |
| `SIMCardTransactionalOutboxIntegrationTest` | Integration | Full lifecycle, atomicity, idempotency, multi-SIM |

### Testing the Atomic Guarantee

```java
@Test
void simStateAndOutboxBothPresentBeforeRelay() {
    SIMCard sim = commandService.handle(new RegisterSIMCommand("ICC", "+447900000001", "CUST-1"));

    // Both written before relay runs — proves atomicity
    assertThat(simCardRepository.findById(sim.getSimId())).isPresent();
    assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);

    // Broker not yet called — relay hasn't run
    assertThat(broker.getPublishedMessages()).isEmpty();
}
```

### Testing Relay Failure Resilience

```java
@Test
void marksMessageFailedWhenBrokerThrows() {
    outboxRepository.save(pendingMessage("SIMActivatedEvent"));
    doThrow(new RuntimeException("Kafka unavailable")).when(publisher).publish(any());

    relayService.relayPendingMessages();

    assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(1);
    // Message remains; will be retried
}
```

### Testing Idempotency

```java
@Test
void relayIsIdempotent() {
    commandService.handle(new RegisterSIMCommand("ICC", "+447900000002", "CUST-2"));

    int firstRun  = relayService.relayPendingMessages();
    int secondRun = relayService.relayPendingMessages();

    assertThat(firstRun).isEqualTo(1);
    assertThat(secondRun).isEqualTo(0);   // Already PUBLISHED — not re-sent
}
```

### Run Tests

```bash
# All outbox tests
./gradlew test --tests "com.saurabhshcs.adtech.microservices.designpattern.outbox.*"

# Single test class
./gradlew test --tests "com.saurabhshcs.adtech.microservices.designpattern.outbox.telco.integration.SIMCardTransactionalOutboxIntegrationTest"
```

---

## Production Considerations

### 1. Real Database Transaction

Annotate the command handler with `@Transactional`:

```java
@Transactional
private void persistAtomically(SIMCard sim) { ... }
```

Both `SIMCardRepository` and `OutboxRepository` must use the **same `DataSource`** (i.e., the same PostgreSQL schema/database). This is the only hard requirement for the pattern to hold.

### 2. Outbox Table Schema (PostgreSQL)

```sql
CREATE TABLE sim_outbox (
    message_id     UUID PRIMARY KEY,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(200) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ,
    retry_count    INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_sim_outbox_status ON sim_outbox(status) WHERE status = 'PENDING';
```

### 3. Relay Scheduling

```java
@Scheduled(fixedDelay = 1000)   // every 1 second
public void scheduledRelay() {
    relayService.relayPendingMessages();
}
```

Or use **Debezium CDC** to capture PostgreSQL WAL changes on the outbox table — lower latency than polling.

### 4. Dead-Letter Strategy

```java
private static final int MAX_RETRIES = 5;

if (message.getRetryCount() >= MAX_RETRIES) {
    message.markDeadLetter();
    // Move to dead-letter queue / alert ops team
}
```

### 5. Kafka Topic Design

```
sim.events
  ├── key: simId             (ensures ordering per SIM)
  └── headers:
        event-type: SIMActivatedEvent
        aggregate-type: SIMCard
        message-id: <UUID>   (deduplication key for at-least-once delivery)
```

### 6. Observability

- **Metric:** `outbox.pending.count` — alert if > 1,000 for more than 5 minutes
- **Metric:** `outbox.failed.count` — alert on any non-zero value
- **Log correlation:** include `messageId` and `aggregateId` in all relay log entries

---

## Comparison: Outbox vs Alternatives

| Approach | Atomicity | Broker failure safe | Complexity |
|---|---|---|---|
| **Transactional Outbox** | ✅ | ✅ — message persisted in DB | Medium |
| Dual-write (no outbox) | ❌ | ❌ — event lost on crash | Low |
| Distributed transaction (2PC) | ✅ | ✅ | High — requires XA driver |
| Event Sourcing (event store as truth) | ✅ | ✅ | High |
| Saga Choreography | ✅ per step | ✅ | Medium-High |

For most telco write workloads, the Transactional Outbox provides the best balance of **reliability vs operational complexity**.

---

## Running the Demo

### Prerequisites

- Java 17+
- No external services required — all repositories are in-memory

### Build & Test

```bash
./gradlew clean build
./gradlew test --tests "com.saurabhshcs.adtech.microservices.designpattern.outbox.*"
```

### Explore the Pattern

The integration test `SIMCardTransactionalOutboxIntegrationTest#fullSIMLifecycle` runs the complete
**Register → Activate → Suspend → Reactivate → Terminate** lifecycle and validates:

- Every command produces exactly one outbox message
- Relay publishes each event in sequence
- All 5 events arrive at the in-memory broker
- No pending messages remain after the final relay run

### View Architecture Diagrams

Render `transactional-outbox-pattern.puml` with any PlantUML-compatible tool:

```bash
# Using PlantUML JAR
java -jar plantuml.jar src/main/resources/docs/transactional-outbox-pattern.puml

# Or use the VS Code PlantUML extension / IntelliJ diagram plugin
```

The file contains four diagrams:
1. **Component architecture** — system-level overview
2. **SIM Activation sequence** — detailed message flow for one activation
3. **SIM card state machine** — all valid state transitions
4. **OutboxMessage lifecycle** — PENDING → PUBLISHED / FAILED states
