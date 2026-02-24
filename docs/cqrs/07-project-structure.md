# Project Structure and Module Breakdown

## Overview

The CQRS eCommerce example is structured as a **multi-module Gradle project**. Each module has a clear responsibility, enforcing the separation between commands (write side) and queries (read side).

---

## Module Structure

```
ecommerce-cqrs/
├── build.gradle.kts                    ← Root build file
├── settings.gradle.kts                 ← Module declarations
│
├── shared/                             ← Shared domain primitives
│   └── src/main/java/com/example/shared/
│       ├── domain/
│       │   ├── event/
│       │   │   └── DomainEvent.java    ← Sealed interface
│       │   └── value/
│       │       ├── Money.java          ← Record
│       │       ├── Address.java        ← Record
│       │       └── EmailAddress.java   ← Record
│       └── exception/
│           └── DomainException.java
│
├── order-command/                      ← Write side
│   └── src/
│       ├── main/java/com/example/order/command/
│       │   ├── api/
│       │   │   ├── OrderCommandController.java
│       │   │   └── dto/
│       │   │       ├── PlaceOrderRequest.java
│       │   │       └── CancelOrderRequest.java
│       │   ├── application/
│       │   │   ├── PlaceOrderCommandHandler.java
│       │   │   ├── CancelOrderCommandHandler.java
│       │   │   ├── UpdateShippingAddressCommandHandler.java
│       │   │   └── MarkOrderShippedCommandHandler.java
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   ├── Order.java
│       │   │   │   ├── OrderItem.java
│       │   │   │   ├── OrderItemSnapshot.java
│       │   │   │   └── OrderStatus.java
│       │   │   ├── event/
│       │   │   │   ├── OrderPlacedEvent.java
│       │   │   │   ├── OrderCancelledEvent.java
│       │   │   │   ├── OrderShippedEvent.java
│       │   │   │   ├── OrderDeliveredEvent.java
│       │   │   │   └── ShippingAddressUpdatedEvent.java
│       │   │   ├── exception/
│       │   │   │   ├── OrderException.java
│       │   │   │   ├── OrderNotFoundException.java
│       │   │   │   ├── InvalidOrderException.java
│       │   │   │   └── InvalidOrderStateException.java
│       │   │   ├── command/
│       │   │   │   ├── PlaceOrderCommand.java
│       │   │   │   ├── CancelOrderCommand.java
│       │   │   │   ├── UpdateShippingAddressCommand.java
│       │   │   │   └── MarkOrderShippedCommand.java
│       │   │   └── repository/
│       │   │       └── OrderRepository.java
│       │   └── infrastructure/
│       │       ├── persistence/
│       │       │   └── JpaOrderRepository.java
│       │       └── messaging/
│       │           └── KafkaDomainEventPublisher.java
│       └── test/java/com/example/order/command/
│           ├── domain/
│           │   └── OrderTest.java
│           ├── application/
│           │   ├── PlaceOrderCommandHandlerTest.java
│           │   └── CancelOrderCommandHandlerTest.java
│           └── integration/
│               └── OrderCommandIntegrationTest.java
│
├── order-query/                        ← Read side
│   └── src/
│       ├── main/java/com/example/order/query/
│       │   ├── api/
│       │   │   └── OrderQueryController.java
│       │   ├── application/
│       │   │   ├── GetOrderByIdQueryHandler.java
│       │   │   ├── GetOrdersByCustomerQueryHandler.java
│       │   │   └── GetOrderStatusQueryHandler.java
│       │   ├── domain/
│       │   │   ├── query/
│       │   │   │   ├── GetOrderByIdQuery.java
│       │   │   │   ├── GetOrdersByCustomerQuery.java
│       │   │   │   └── GetOrderStatusQuery.java
│       │   │   ├── view/
│       │   │   │   ├── OrderDetailView.java
│       │   │   │   ├── OrderSummaryView.java
│       │   │   │   ├── OrderItemView.java
│       │   │   │   ├── OrderStatusView.java
│       │   │   │   └── AddressView.java
│       │   │   └── repository/
│       │   │       └── OrderReadRepository.java
│       │   └── infrastructure/
│       │       ├── persistence/
│       │       │   └── MongoOrderReadRepository.java
│       │       └── projection/
│       │           ├── OrderSummaryProjector.java
│       │           └── OrderDetailProjector.java
│       └── test/java/com/example/order/query/
│           ├── application/
│           │   └── GetOrderByIdQueryHandlerTest.java
│           ├── infrastructure/
│           │   └── OrderSummaryProjectorTest.java
│           └── integration/
│               └── OrderQueryIntegrationTest.java
│
├── product-command/                    ← Product write side
├── product-query/                      ← Product read side
├── customer-command/                   ← Customer write side
└── customer-query/                     ← Customer read side
```

---

## Module Dependencies

```
                        ┌──────────────┐
                        │   shared     │
                        │ (DomainEvent,│
                        │  Value Objs) │
                        └──────┬───────┘
                               │
               ┌───────────────┴───────────────┐
               │                               │
        ┌──────▼──────┐                 ┌──────▼──────┐
        │order-command│                 │ order-query  │
        │ (write side)│                 │ (read side)  │
        └──────┬──────┘                 └──────┬───────┘
               │                               │
               │   Kafka Events                │
               └────────────────────────────► Projectors
```

Dependencies:
- `order-command` depends on `shared`
- `order-query` depends on `shared`
- `order-command` and `order-query` do **not** depend on each other — they communicate only via Kafka events

---

## settings.gradle.kts

```kotlin
rootProject.name = "ecommerce-cqrs"

include(
    "shared",
    "order-command",
    "order-query",
    "product-command",
    "product-query",
    "customer-command",
    "customer-query"
)
```

---

## Root build.gradle.kts

```kotlin
plugins {
    id("org.springframework.boot") version "3.3.0" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
    java
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.assertj:assertj-core")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

---

## API Design

### Command Endpoints (POST / DELETE / PATCH)

| Method | Endpoint | Command | Response |
|--------|----------|---------|----------|
| POST | `/api/orders` | `PlaceOrderCommand` | `201 Created` + `{ orderId }` |
| DELETE | `/api/orders/{orderId}` | `CancelOrderCommand` | `204 No Content` |
| PATCH | `/api/orders/{orderId}/shipping-address` | `UpdateShippingAddressCommand` | `204 No Content` |
| POST | `/api/orders/{orderId}/ship` | `MarkOrderShippedCommand` | `204 No Content` |
| POST | `/api/orders/{orderId}/deliver` | `MarkOrderDeliveredCommand` | `204 No Content` |

### Query Endpoints (GET)

| Method | Endpoint | Query | Response |
|--------|----------|-------|----------|
| GET | `/api/orders/{orderId}` | `GetOrderByIdQuery` | `200 OK` + `OrderDetailView` |
| GET | `/api/orders?customerId={id}&page=0&size=20` | `GetOrdersByCustomerQuery` | `200 OK` + `Page<OrderSummaryView>` |
| GET | `/api/orders/{orderId}/status` | `GetOrderStatusQuery` | `200 OK` + `OrderStatusView` |
| GET | `/api/products?search={term}&category={cat}` | `SearchProductsQuery` | `200 OK` + `Page<ProductView>` |

---

## Infrastructure Components

### Kafka Topics

| Topic | Event | Partitions | Retention |
|-------|-------|------------|-----------|
| `order-placed` | `OrderPlacedEvent` | 12 | 7 days |
| `order-cancelled` | `OrderCancelledEvent` | 6 | 7 days |
| `order-shipped` | `OrderShippedEvent` | 6 | 7 days |
| `order-delivered` | `OrderDeliveredEvent` | 6 | 7 days |
| `shipping-address-updated` | `ShippingAddressUpdatedEvent` | 6 | 7 days |

### Databases

| Store | Technology | Used For |
|-------|-----------|----------|
| Write Store | PostgreSQL 16 | `orders`, `order_items` tables |
| Read Store | MongoDB 7 | `order_summary_view`, `order_detail_view` collections |
| Cache | Redis 7 | Frequently accessed read models |
| Event Store (optional) | EventStoreDB | Full event history for replay |

---

## Docker Compose (Local Development)

```yaml
# docker-compose.yml
version: '3.9'

services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ecommerce_write
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"

  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  redis:
    image: redis:7
    ports:
      - "6379:6379"
```

---

## Implementation Roadmap

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 1 | Domain model — aggregates, value objects, enums | Planned |
| 2 | Command side — commands, handlers, `Order` aggregate | Planned |
| 3 | Domain events — definitions, publishing via Kafka | Planned |
| 4 | Query side — read models, projectors, query handlers | Planned |
| 5 | API layer — REST controllers for commands and queries | Planned |
| 6 | Integration tests with Testcontainers | Planned |
| 7 | Performance optimisation — caching, virtual threads | Planned |

---

## Related Docs

- [01-overview.md](./01-overview.md) — Pattern overview and trade-offs
- [02-ecommerce-domain.md](./02-ecommerce-domain.md) — Domain model
- [03-commands.md](./03-commands.md) — Write side
- [04-queries.md](./04-queries.md) — Read side
- [05-events.md](./05-events.md) — Domain events and projectors
- [06-java-implementation.md](./06-java-implementation.md) — Java 21 features
- [eCommerceOrderCQRS.puml](./eCommerceOrderCQRS.puml) — Sequence diagram
- [../choreography/UserServiceChoreography.md](../choreography/UserServiceChoreography.md) — Saga Choreography
- [../orchestration/UserServiceOrchestrator.md](../orchestration/UserServiceOrchestrator.md) — Saga Orchestration
