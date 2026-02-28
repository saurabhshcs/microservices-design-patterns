# Transactional Outbox Pattern

**One-line description:** Guarantee reliable event publishing by writing the outgoing event into a database table within the same ACID transaction as the business data change, then asynchronously relaying it to the message broker.

---

## When to Use This Pattern

- Your service must **publish events to a message broker** (Kafka, RabbitMQ) after modifying its own database, and **you cannot tolerate lost messages**.
- You need **at-least-once delivery** guarantees without resorting to distributed transactions (XA / 2PC).
- The domain demands **causal ordering** -- events must be published in the order they were written.
- You are building an **event-driven microservice** where downstream consumers depend on receiving every state change.
- Compliance or audit requirements dictate that every outbound event is recorded before dispatch.

## When NOT to Use This Pattern

- Your message broker supports **transactional producers** natively (e.g., Kafka Transactions) and you can tolerate the coupling.
- The downstream consumer can regenerate missed events by polling the source system (query-based integration).
- The system is a monolith where in-process event publishing (`ApplicationEventPublisher`) is sufficient.

---

## Key Components and Roles

| Component | Role |
|-----------|------|
| **Business Service** | Executes the business operation and writes to the domain table AND the outbox table within a single transaction |
| **Outbox Table** | A database table (`outbox_events`) that stores pending events as rows, co-located with the business data |
| **OutboxEvent Entity** | JPA entity representing a single outbox row (event type, payload, status, created timestamp) |
| **Outbox Relay / Poller** | A scheduled component that reads unpublished outbox rows and sends them to the message broker |
| **Message Broker** | External system (Kafka) that receives events from the relay |
| **Debezium CDC (alternative)** | A Change Data Capture connector that tails the database WAL and pushes outbox inserts directly to Kafka, eliminating the need for polling |

---

## The Dual-Write Problem

```
   Service
     |
     +---> (1) Write to DB        -- succeeds
     |
     +---> (2) Publish to Kafka   -- FAILS (broker down, network timeout)
     |
     Result: DB has the data, but Kafka never got the event.
             Downstream services are permanently out of sync.
```

The Transactional Outbox solves this by making step (2) part of step (1):

```
   Service
     |
     +---> (1a) Write to business table   }  same ACID
     +---> (1b) Write to outbox table     }  transaction
     |
     +---> (2) Relay polls outbox --> publishes to Kafka (async, retryable)
```

---

## Documentation Index

| File | Contents |
|------|----------|
| [scenario.md](./scenario.md) | Real-world payment processing scenario with architecture diagrams |
| [implementation.md](./implementation.md) | Complete Spring Boot 4.x Java implementation with outbox poller |
| [dependencies.md](./dependencies.md) | Maven/Gradle dependencies, database schemas, and Kafka configuration |

---

## Benefits and Trade-offs

| Benefit | Trade-off |
|---------|-----------|
| No lost events -- at-least-once delivery guaranteed | Consumers must implement idempotent processing |
| No distributed transactions (2PC/XA) needed | Additional database table (outbox) adds storage and I/O overhead |
| Works with any message broker (Kafka, RabbitMQ, SQS) | Polling introduces latency (configurable, typically 100-500ms) |
| Events are auditable -- stored in DB before dispatch | Outbox table must be periodically cleaned up |
| Simple to implement and reason about | Tight coupling between service and its database schema |
| Per-aggregate ordering preserved via sequence_id | Global ordering across all aggregates is not guaranteed |

## Best Practices

1. **Always write business data and outbox event in the same `@Transactional` boundary.** This is the core invariant of the pattern. Breaking it reintroduces the dual-write problem.
2. **Use `FOR UPDATE SKIP LOCKED` in the poller query.** This allows safe horizontal scaling of poller instances without row-level contention.
3. **Include a unique `eventId` in every event payload.** Consumers use this UUID to deduplicate events in at-least-once delivery scenarios.
4. **Keep outbox payloads small (< 1 KB).** For large payloads, use the claim-check pattern: store the payload in object storage (S3) and put the URL in the outbox.
5. **Schedule cleanup of sent events.** Delete `SENT` events older than the retention period (e.g., 7 days) to prevent unbounded table growth.
6. **Use Kafka partition key = `aggregate_id`.** This ensures all events for one aggregate land on the same partition, preserving per-aggregate ordering.
7. **Consider Debezium CDC for high-throughput systems.** When event volume exceeds ~100 events/sec, CDC tailing the WAL is more efficient than polling.
8. **Monitor outbox table depth.** Alert if the number of `PENDING` rows exceeds a threshold -- this indicates the relay is falling behind or Kafka is unhealthy.

---

*Last updated: 2026-02-28*
