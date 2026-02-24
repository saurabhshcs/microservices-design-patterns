# CQRS Design Pattern — eCommerce Example

## What Is CQRS?

**Command Query Responsibility Segregation (CQRS)** is a pattern that separates the **write model** (Commands) from the **read model** (Queries). Instead of a single model that handles both reads and writes, CQRS uses two distinct models optimised for their specific purpose.

> "A method should either change state or return a result, but not both." — Bertrand Meyer (Command-Query Separation principle)

CQRS takes this principle to the architectural level, applying it to entire service models.

---

## Documents in This Guide

| Document | Description |
|----------|-------------|
| [01-overview.md](./01-overview.md) | Pattern overview, trade-offs, and when to use CQRS |
| [02-ecommerce-domain.md](./02-ecommerce-domain.md) | eCommerce domain model — aggregates, entities, value objects |
| [03-commands.md](./03-commands.md) | Write side — commands, command handlers, and validation |
| [04-queries.md](./04-queries.md) | Read side — queries, projections, and read models |
| [05-events.md](./05-events.md) | Domain events — event publishing and projection rebuilding |
| [06-java-implementation.md](./06-java-implementation.md) | Java 21 implementation guide with code examples |
| [07-project-structure.md](./07-project-structure.md) | Module breakdown and package structure |
| [eCommerceOrderCQRS.puml](./eCommerceOrderCQRS.puml) | PlantUML sequence diagram |

---

## Quick Concept Map

```
┌─────────────────────────────────────────────────────────┐
│                     Client / API Layer                   │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
          Command Bus                 Query Bus
               │                          │
               ▼                          ▼
    ┌──────────────────┐       ┌──────────────────────┐
    │   Write Model    │       │     Read Model       │
    │  (Command Side)  │       │    (Query Side)      │
    │                  │       │                      │
    │ - Aggregates     │       │ - Projections        │
    │ - Domain Logic   │       │ - Denormalised Views │
    │ - Validation     │       │ - Optimised Queries  │
    └────────┬─────────┘       └──────────────────────┘
             │                          ▲
             │  Domain Events           │
             └──────────────────────────┘
                  (Event Store / Message Bus)
```

---

## Technology Stack (Planned Implementation)

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build Tool | Gradle |
| Command Bus | Custom / Axon Framework |
| Event Store | PostgreSQL / EventStoreDB |
| Read Store | MongoDB / Redis |
| Messaging | Apache Kafka |
| Testing | JUnit 5, Testcontainers |

---

## Related Patterns

- **Event Sourcing** — CQRS pairs naturally with Event Sourcing (ES). Instead of storing current state, every change is stored as an event. The read model is rebuilt by replaying events.
- **Saga Pattern** — See [../choreography/](../choreography/) and [../orchestration/](../orchestration/) for coordinating distributed transactions that often arise alongside CQRS.
- **Domain-Driven Design (DDD)** — CQRS aligns closely with DDD aggregates, bounded contexts, and domain events.
