# CQRS -- Command Query Responsibility Segregation

**One-line description:** Separate the data-mutation (command) model from the data-retrieval (query) model so each side can be optimised, scaled, and evolved independently.

---

## When to Use This Pattern

- Read and write workloads have **drastically different throughput or latency requirements** (e.g., 100:1 read-to-write ratio).
- The **read schema** needs to be denormalised for fast queries while the **write schema** must stay normalised for integrity.
- You need an **audit trail** of every state-changing operation (commands become first-class citizens).
- Different teams own the read and write pipelines and need to deploy independently.
- The domain is complex enough that a single model becomes a maintenance bottleneck (classic DDD bounded-context split).
- You plan to adopt **Event Sourcing** in the future -- CQRS is a natural prerequisite.

## When NOT to Use This Pattern

- Simple CRUD applications where reads and writes share the same shape.
- Teams without the operational maturity to manage eventual consistency between the two models.
- Prototypes or MVPs where speed-to-market outweighs architectural purity.

---

## Key Components and Roles

| Component | Role |
|-----------|------|
| **Command** | A value object representing an intent to change state (e.g., `PlaceOrderCommand`) |
| **Command Handler** | Validates the command, applies business rules, persists changes to the **write store** |
| **Domain Event** | Immutable record of a state change (e.g., `OrderPlacedEvent`), published after a command succeeds |
| **Event Bus / Application Events** | Transports domain events from the write side to the read side |
| **Query** | A value object describing the data a client wants (e.g., `GetOrdersByCustomerQuery`) |
| **Query Handler** | Fetches data from the **read store** using optimised projections |
| **Read Model Projector** | Listens for domain events and updates the read-optimised store |
| **Write Store** | Normalised relational database (PostgreSQL) optimised for transactional integrity |
| **Read Store** | Denormalised view (separate PostgreSQL schema, or ElasticSearch, Redis, etc.) optimised for query speed |

---

## Documentation Index

| File | Contents |
|------|----------|
| [scenario.md](./scenario.md) | Real-world e-commerce order management scenario with architecture diagrams |
| [implementation.md](./implementation.md) | Complete Spring Boot 4.x Java implementation with all layers |
| [dependencies.md](./dependencies.md) | Maven/Gradle dependencies with justifications and database schemas |

---

## Architecture at a Glance

```
                         +------------------+
                         |   REST Client    |
                         +--------+---------+
                                  |
                    +-------------+-------------+
                    |                           |
              POST /orders                GET /orders
              (Commands)                  (Queries)
                    |                           |
           +--------v--------+        +--------v--------+
           | Command Handler |        |  Query Handler   |
           +--------+--------+        +--------+--------+
                    |                           |
           +--------v--------+        +--------v--------+
           |  Write Store    |        |   Read Store     |
           |  (Normalised)   |        | (Denormalised)   |
           +--------+--------+        +--------^--------+
                    |                           |
                    +--- Domain Events ---------+
                         (async projection)
```

---

## Benefits and Trade-offs

| Benefit | Trade-off |
|---------|-----------|
| Independent scaling of reads and writes | Two data stores to maintain and synchronise |
| Schema optimised per access pattern (denormalised reads, normalised writes) | Eventual consistency between models requires careful UX design |
| Full audit trail via commands and domain events | Increased code complexity (handlers, projectors, event classes) |
| Teams can deploy read and write sides independently | Debugging requires tracing through events across models |
| Natural stepping stone to Event Sourcing | Idempotent projectors are essential to avoid data corruption |
| Read replicas can be cached aggressively | Stale reads are possible; mitigate with version stamping |

## Best Practices

1. **Start with a single database, two schemas.** Use PostgreSQL `CREATE SCHEMA write_store` and `CREATE SCHEMA read_store` within the same database during development. Migrate to separate databases only when scaling demands it.
2. **Keep events self-contained.** Each domain event should carry all data needed by projectors so they never need to query the write store.
3. **Make projectors idempotent.** Use `UPSERT` (Postgres `ON CONFLICT DO UPDATE`) so that reprocessing the same event produces the same result.
4. **Use `@TransactionalEventListener(AFTER_COMMIT)`.** Projecting before the command transaction commits can create phantom reads in the read model.
5. **Implement read-your-own-writes.** After a command, redirect the user to a confirmation page that reads from the write store, not the eventually-consistent read model.
6. **Monitor the consistency gap.** Track the lag between the last command timestamp and the last projected event timestamp. Alert if it exceeds your SLA.
7. **Do not apply CQRS to every service.** Reserve it for bounded contexts with genuine read/write asymmetry. Simple CRUD services gain nothing from the added complexity.
8. **Version your events.** Add a `schemaVersion` field so projectors can handle schema evolution without downtime.

---

*Last updated: 2026-02-28*
