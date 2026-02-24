# User Service — Saga Orchestration Pattern

## What Is It?

The **Orchestration** style of the Saga pattern uses a **central controller** — the Orchestrator — to drive every step of a multi-service workflow. It knows the full sequence, calls each service directly, and is solely responsible for triggering compensation if anything goes wrong.

Think of it like a **conductor in an orchestra**: the conductor tells each musician (service) when to play, what to play, and pauses the performance to fix mistakes.

---

## When Should You Use It?

| Use Orchestration when…                                    | Avoid it when…                                        |
|------------------------------------------------------------|-------------------------------------------------------|
| You need a clear, auditable sequence of steps             | Services are owned by independent teams              |
| Business logic is complex with conditional branching      | You want loose coupling and independent deployability |
| You need a single place to monitor and retry workflow     | The workflow is simple and linear                    |
| Compensation logic is non-trivial                         | Event-driven architecture is already established     |

---

## How It Works — Step by Step

The `UserServiceOrchestrator` manages the **user onboarding saga**: a 4-step workflow that must succeed atomically.

### Workflow Steps

```
STARTED
   │
   ▼
USER_CREATED        ← UserRegistrationService validates & persists the user
   │
   ▼
EMAIL_SENT          ← EmailService sends a welcome email
   │
   ▼
PREFERENCES_SET     ← PreferenceService initialises default preferences
   │
   ▼
COMPLETED           ← AuditService logs the successful creation
```

### On Failure — Compensation

If **any step fails**, the Orchestrator immediately:
1. Stops the forward workflow
2. Calls compensating operations on all **already-completed** steps (in reverse order)
3. Sets the final state to `FAILED`

```
EMAIL_SENT fails
       │
       ▼
deleteUser(userId)          ← undo USER_CREATED
       │
       ▼
logCompensation(userId)     ← audit trail
       │
       ▼
FAILED { reason: EMAIL_FAILED }
```

---

## Sequence Diagram

> See [`UserServiceOrchestrator.puml`](./UserServiceOrchestrator.puml) for the full PlantUML source.

```
Client ──► Orchestrator ──► UserRegistrationService ──► DB
                        ──► EmailService
                        ──► PreferenceService
                        ──► AuditService
           ◄── COMPLETED / FAILED
```

**Key observations from the diagram:**

- The **Orchestrator is the only participant that talks to all services**
- Services are **stateless** — they do one job and return a result
- The **Orchestrator holds all state transitions** (USER_CREATED → EMAIL_SENT → etc.)
- Compensation flows **backwards** through completed steps only

---

## Code Reference

### Entry Point

```java
// UserServiceOrchestrator.java
UserModel userModel = UserModel.builder()
    .userName(SAMEER)
    .email(USER_EMAIL)
    .build();
// → triggers the saga: validate → email → preferences → audit
```

### State Machine (OrchestratorState)

```java
public enum OrchestratorState {
    STARTED,
    USER_CREATED,
    EMAIL_SENT,
    PREFERENCES_SET,
    COMPLETED,
    FAILED
}
```

### Compensation Pattern

```java
private void compensate(UUID userId) {
    // Called in reverse order of completion:
    // 1. deleteUser(userId)        — undo USER_CREATED
    // 2. logCompensation(userId)   — audit trail
    // State → FAILED
}
```

---

## Pros and Cons

### Pros
- **Centralised control** — easy to read, debug, and monitor the full workflow
- **Explicit compensation** — one place to define rollback logic
- **Testable** — each step can be mocked and tested in isolation
- **Visibility** — a single class shows the entire business process

### Cons
- **Single point of failure** — if the Orchestrator crashes, the saga stops
- **Tight coupling** — the Orchestrator must know about every service
- **Bottleneck** — all traffic flows through one component
- **God class risk** — business logic can accumulate in the Orchestrator

---

## Architecture Decision

> Choose Orchestration when you need **strong consistency guarantees**, **auditability**, and **complex compensation logic**. If your services are independently owned or you are building an event-driven system, prefer [Choreography](../choreography/UserServiceChoreography.md).

---

## Related

- [`UserServiceChoreography.md`](../choreography/UserServiceChoreography.md) — the event-driven alternative
- [`CampaignOrchestrator.java`](../../src/main/java/com/saurabhshcs/adtech/microservices/designpattern/saga/CampaignOrchestrator.java) — working orchestration implementation
- [`OrchestratorState.java`](../../src/main/java/com/saurabhshcs/adtech/microservices/designpattern/saga/common/OrchestratorState.java) — state machine enum
