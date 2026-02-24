# CQRS Overview — Concept, Trade-offs, and When to Use It

## The Core Problem CQRS Solves

In a traditional CRUD architecture, a single model serves both read and write operations. This creates a fundamental tension:

- **Write operations** need strong consistency, validation, domain logic, and transactional guarantees.
- **Read operations** need speed, flexibility in data shape, and the ability to join/aggregate across entities.

Optimising one side often degrades the other. CQRS resolves this by using **two separate models**.

---

## The Two Sides

### Command Side (Write Model)

Responsible for **changing state**. Commands are imperative: `PlaceOrder`, `CancelOrder`, `UpdateShippingAddress`.

- Commands are **validated** before being handled.
- The **aggregate** enforces domain invariants (business rules).
- On success, the aggregate emits one or more **domain events**.
- The write store is typically a **relational database** or **event store** — normalised, consistent.

### Query Side (Read Model)

Responsible for **returning data**. Queries are descriptive: `GetOrderById`, `GetOrdersByCustomer`, `GetTopSellingProducts`.

- Queries **never change state**.
- Read models are **denormalised projections** — pre-joined, pre-aggregated views.
- They are built and updated by **reacting to domain events**.
- The read store can be optimised per use case: **MongoDB**, **Redis**, **Elasticsearch**, **PostgreSQL views**.

---

## How It Works — Data Flow

```
                        ┌───────────────────────────────┐
                        │         API Gateway            │
                        └────────┬──────────────┬────────┘
                                 │              │
                           Command           Query
                             Bus               Bus
                                 │              │
               ┌─────────────────▼──┐    ┌─────▼──────────────────┐
               │   Command Handler  │    │    Query Handler        │
               │                    │    │                         │
               │  1. Validate       │    │  1. Load from           │
               │  2. Load aggregate │    │     Read Store          │
               │  3. Execute domain │    │  2. Return DTO          │
               │     logic          │    │                         │
               │  4. Persist        │    └─────────────────────────┘
               │  5. Publish events │              ▲
               └─────────┬──────────┘              │
                         │   Domain Events          │ (projector updates
                         └──────────────────────────┘  read store)
```

---

## When Should You Use CQRS?

| Use CQRS when…                                                  | Avoid CQRS when…                                               |
|------------------------------------------------------------------|----------------------------------------------------------------|
| Read and write workloads scale independently                     | The domain is simple CRUD with no business rules               |
| Read models need different shapes than the write model           | Team is small and the added complexity slows delivery          |
| Complex domain logic on the write side (DDD aggregates)          | Read and write volumes are roughly equal and low               |
| High read-to-write ratio (e.g., product catalogue: 1000:1 reads) | Strong consistency is required for reads (CQRS is eventually consistent) |
| Different data stores are optimal for reads vs writes            | You need a quick prototype or MVP                              |
| Audit trail or event replay is required                          |                                                                |

---

## CQRS vs Traditional Architecture

| Concern | Traditional (CRUD) | CQRS |
|---------|--------------------|------|
| Data model | Single model | Separate read/write models |
| Read performance | Limited by write model shape | Optimised projections per use case |
| Write complexity | Domain logic mixed with persistence | Encapsulated in aggregates |
| Consistency | Strong (synchronous) | Eventual (read model lags slightly) |
| Scalability | Scale the whole service | Scale read/write sides independently |
| Auditability | Difficult — state overwritten | Natural with Event Sourcing |
| Learning curve | Low | Higher — two models to maintain |

---

## CQRS and Event Sourcing

CQRS does **not require** Event Sourcing, but they complement each other naturally:

- **Without Event Sourcing**: The command side updates a database (current state). Domain events are published to update read models. Current state is lost after each update.
- **With Event Sourcing**: Every state change is stored as an immutable event. Current state is derived by replaying events. Read models are rebuilt from the event stream at any time.

This guide focuses on **CQRS without Event Sourcing** for clarity, with notes on where Event Sourcing would slot in.

---

## eCommerce Context

In our eCommerce example, CQRS is applied to the **Order Management** bounded context:

- **Commands**: `PlaceOrderCommand`, `CancelOrderCommand`, `UpdateShippingAddressCommand`, `MarkOrderShippedCommand`
- **Queries**: `GetOrderByIdQuery`, `GetOrdersByCustomerQuery`, `GetOrderSummaryQuery`, `GetOrderStatusQuery`
- **Events**: `OrderPlacedEvent`, `OrderCancelledEvent`, `ShippingAddressUpdatedEvent`, `OrderShippedEvent`

> See [02-ecommerce-domain.md](./02-ecommerce-domain.md) for the full domain model.

---

## Key Terminology

| Term | Definition |
|------|-----------|
| **Command** | An intent to change system state. Named in imperative form (`PlaceOrder`). |
| **Query** | A request for data. Never changes state. Named as a question (`GetOrderById`). |
| **Aggregate** | A cluster of domain objects treated as a single unit. The write model's consistency boundary. |
| **Domain Event** | A record of something that happened (`OrderPlaced`). Used to update read models. |
| **Projection** | A read model built by processing domain events. Optimised for a specific query. |
| **Command Handler** | Receives a command, loads the aggregate, executes business logic, persists, publishes events. |
| **Query Handler** | Receives a query, reads from the read store, returns a DTO. |
| **Event Handler / Projector** | Subscribes to domain events and updates the read store. |
