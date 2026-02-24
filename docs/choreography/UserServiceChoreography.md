# User Service — Saga Choreography Pattern

## What Is It?

The **Choreography** style of the Saga pattern has **no central controller**. Instead, each service reacts to events published by other services. Services are autonomous — they subscribe to events they care about, do their work, and publish a new event when done.

Think of it like a **jazz band**: no conductor, each musician listens to what the others are playing and responds accordingly.

---

## When Should You Use It?

| Use Choreography when…                                      | Avoid it when…                                           |
|-------------------------------------------------------------|----------------------------------------------------------|
| Services are owned by independent teams                     | Workflow is complex with many conditional branches       |
| You are building an event-driven architecture               | You need a clear, auditable view of the full workflow   |
| You want loose coupling and independent deployability       | Compensation logic is complex and must be coordinated   |
| Simple, linear workflows                                    | Debugging distributed event chains is a concern         |

---

## How It Works — Step by Step

`UserServiceChoreography` implements user onboarding as a **chain of events**. Each service listens on the event bus, does its job, and publishes the next event.

### Event Chain — Happy Path

```
Client
  │ POST /users
  ▼
UserRegistrationService  ──publish──► UserRegisteredEvent
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                         EmailService   PreferenceService  AuditService
                              │               │
                        publish         publish
                              ▼               ▼
                   WelcomeEmailSentEvent  PreferencesInitialisedEvent
                                              │
                                         AuditService (logs both)
```

### Event Chain — Failure & Compensation

```
EmailService FAILS
  │
  └──publish──► UserRegistrationFailedEvent
                        │
          ┌─────────────┤
          ▼             ▼
UserRegistrationService  AuditService
(deleteUser — compensate)  (log failure)
          │
          └──publish──► UserRegistrationCompensatedEvent
                                │
                           AuditService (log compensation)
```

---

## Events Reference

| Event                            | Publisher                  | Subscribers                              | Meaning                                   |
|----------------------------------|----------------------------|------------------------------------------|-------------------------------------------|
| `UserRegisteredEvent`            | UserRegistrationService    | EmailService, PreferenceService, Audit   | User record created in DB                 |
| `WelcomeEmailSentEvent`          | EmailService               | AuditService                             | Welcome email delivered                   |
| `PreferencesInitialisedEvent`    | PreferenceService          | AuditService                             | Default preferences set                   |
| `UserRegistrationFailedEvent`    | EmailService               | UserRegistrationService, AuditService    | A step failed — trigger compensation      |
| `UserRegistrationCompensatedEvent` | UserRegistrationService  | AuditService                             | User record deleted (rolled back)         |

---

## Sequence Diagram

> See [`UserServiceChoreography.puml`](./UserServiceChoreography.puml) for the full PlantUML source.

```
Client ──► UserRegistrationService ──publish──► [Event Bus]
                                                     │
                                      ┌──────────────┼──────────────┐
                                      ▼              ▼              ▼
                                EmailService  PreferenceService  AuditService
```

**Key observations from the diagram:**

- **No service calls another service directly** — all communication is via the event bus
- Services are **decoupled** — EmailService does not know PreferenceService exists
- The **Client receives `202 Accepted` immediately** — the saga continues asynchronously
- Compensation is **event-driven** — a `UserRegistrationFailedEvent` triggers rollback automatically
- **AuditService subscribes to everything** — providing full observability without coupling to business logic

---

## Important: Eventual Consistency

> In the Choreography pattern, the client gets an **immediate `202 Accepted`** response. The saga continues in the background. The client **does not know** the final outcome synchronously.

To handle this, the client must either:
- **Poll** a status endpoint: `GET /users/{userId}/status`
- **Subscribe** to a completion event (e.g. via WebSocket or SSE)
- **Accept eventual consistency** (e.g. show "Registration in progress…")

This is fundamentally different from Orchestration, where the client waits for a synchronous `COMPLETED` or `FAILED` response.

---

## Code Reference

### Event Publishing (UserRegistrationService)

```java
// After persisting the user:
eventBus.publish(new UserRegisteredEvent(userId, email));
// Return 202 immediately — do NOT wait for downstream services
```

### Event Handling (EmailService)

```java
@EventListener(UserRegisteredEvent.class)
public void onUserRegistered(UserRegisteredEvent event) {
    try {
        emailClient.sendWelcome(event.getEmail());
        eventBus.publish(new WelcomeEmailSentEvent(event.getUserId()));
    } catch (Exception e) {
        eventBus.publish(new UserRegistrationFailedEvent(
            event.getUserId(), "EMAIL_FAILED"));
    }
}
```

### Compensation Handler (UserRegistrationService)

```java
@EventListener(UserRegistrationFailedEvent.class)
public void onRegistrationFailed(UserRegistrationFailedEvent event) {
    userRepository.deleteById(event.getUserId());
    eventBus.publish(new UserRegistrationCompensatedEvent(event.getUserId()));
}
```

---

## Pros and Cons

### Pros
- **Loose coupling** — services know only about events, not about each other
- **Independent deployability** — each service can be deployed, scaled, and updated independently
- **Resilience** — one service going down does not block others from processing events
- **Natural fit for event-driven systems** — Kafka, RabbitMQ, AWS SNS/SQS

### Cons
- **Hard to trace** — the full workflow is spread across many services and topics
- **Complex debugging** — a missing event can silently stall the saga
- **No single source of truth** — workflow state must be inferred from events
- **Eventual consistency challenges** — the client must handle asynchronous outcomes
- **Cyclic dependency risk** — event chains can create hard-to-detect cycles

---

## Observability Tip

Because there is no central Orchestrator, **distributed tracing is essential**:
- Attach a **correlation ID** (`sagaId`) to every event
- Use tools like **Zipkin**, **Jaeger**, or **AWS X-Ray** to trace event chains
- Centralise logs in **ELK / CloudWatch** and filter by `sagaId`

---

## Architecture Decision

> Choose Choreography when services are **independently owned**, your infrastructure is **event-driven** (Kafka/RabbitMQ), and you can tolerate **eventual consistency**. If you need **strong transactional guarantees** or **complex conditional branching**, prefer [Orchestration](../orchestration/UserServiceOrchestrator.md).

---

## Related

- [`UserServiceOrchestrator.md`](../orchestration/UserServiceOrchestrator.md) — the centralised alternative
- [`CampaignOrchestrator.java`](../../src/main/java/com/saurabhshcs/adtech/microservices/designpattern/saga/CampaignOrchestrator.java) — working orchestration reference implementation
- [`OrchestratorState.java`](../../src/main/java/com/saurabhshcs/adtech/microservices/designpattern/saga/common/OrchestratorState.java) — state machine enum
