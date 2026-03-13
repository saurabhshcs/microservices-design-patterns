# Microservice Design Patterns — Java & Spring Boot

A comprehensive, hands-on training project demonstrating **10 microservice design patterns** implemented in Java 17 and Spring Boot 3.4.2. Every pattern is applied to a real industry domain (banking, financial trading, eCommerce, payment) and is fully covered by TDD unit tests.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](#running-tests)
[![Tests](https://img.shields.io/badge/tests-160%20passing-brightgreen)](#running-tests)
[![Coverage](https://img.shields.io/badge/line%20coverage-99%25-brightgreen)](#test-coverage)
[![Branch Coverage](https://img.shields.io/badge/branch%20coverage-100%25-brightgreen)](#test-coverage)
[![Java](https://img.shields.io/badge/java-17-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.4.2-green)](https://spring.io/projects/spring-boot)

---

## Table of Contents

- [Project Overview](#project-overview)
- [Patterns Implemented](#patterns-implemented)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [Test Coverage](#test-coverage)
- [Architecture Diagrams](#architecture-diagrams)
- [Confluence Documentation](#confluence-documentation)
- [Pattern Quick Reference](#pattern-quick-reference)
- [Contributing](#contributing)
- [Author](#author)

---

## Project Overview

This single-module Spring Boot project demonstrates 10 battle-tested microservice design patterns in one codebase, making it easy to compare, contrast, and learn from each implementation side-by-side.

| Category | Patterns |
|----------|----------|
| **Distributed Transactions** | Saga Orchestration, Saga Choreography |
| **Data Management** | CQRS + Event Sourcing, EventStore |
| **Resilience** | Circuit Breaker, Bulkhead |
| **Infrastructure** | API Gateway, Sidecar |
| **Migration** | Strangler Fig |
| **Communication** | Event-Driven |

> **Design decision:** All patterns live in a single module intentionally — this makes it easier to compare implementations side-by-side during training. In production, each would be its own deployable service.

---

## Patterns Implemented

### 1. Saga — Orchestration
**Domain:** Ad-tech Campaign Launch & eCommerce Order Processing

A central orchestrator drives a multi-step workflow with explicit compensation on failure.

- **Ad-tech:** `CampaignOrchestrator` — 3-step campaign launch (budget → inventory → schedule)
  State machine: `STARTED → BUDGET_VALIDATED → INVENTORY_RESERVED → SCHEDULED → COMPLETED / FAILED`
- **eCommerce:** `OrderSagaOrchestrator` — Payment → Inventory → Shipping saga steps implement `SagaStep<Order>`; compensation runs in LIFO order on any failure.

```java
// Ad-tech
OrchestratorState result = orchestrator.execute(campaignId, true, true, true);
// → COMPLETED

// eCommerce
Order order = sagaOrchestrator.processOrder(Order.create("CUST-1", "PROD-1", 2, new BigDecimal("200")));
// → OrderStatus.COMPLETED
```

---

### 2. Saga — Choreography
**Domain:** Ad-tech User Service

Event-driven saga coordination without a central controller. Each service publishes events and other services react autonomously.

---

### 3. CQRS + Event Sourcing
**Domain:** Banking Account Management

Separate write (command) and read (query) models using Java 17 sealed types.

- **Write side:** `AccountCommandService` executes commands on the `Account` aggregate, publishes domain events
- **Read side:** `AccountProjection` listens to events and updates `AccountView` in `InMemoryAccountReadRepository`

```java
UUID id = commandService.handle(
    new AccountCommand.CreateAccountCommand("OWN-1", "Alice", new BigDecimal("1000")));
commandService.handle(new AccountCommand.DepositMoneyCommand(id, new BigDecimal("250"), "Salary"));

AccountView view = queryService.getAccount(id).orElseThrow();
// view.getBalance() == 1250
```

---

### 4. EventStore
**Domain:** Banking CQRS Extension

Append-only event log enabling full event sourcing — persist domain events and replay them to rebuild aggregate state.

```java
eventStore.append(accountId, List.of(openedEvent, depositedEvent));

List<AccountEvent> full    = eventStore.loadEvents(accountId);
List<AccountEvent> recent  = eventStore.loadEventsSince(accountId, Instant.now().minusHours(1));
List<AccountEvent> all     = eventStore.loadAllEvents(); // global stream for projection rebuild
```

---

### 5. API Gateway
**Domain:** RetailHub eCommerce Platform

Reactive gateway (WebFlux / Netty) providing a unified entry point with security, observability, and resilience.

| Feature | Implementation |
|---------|---------------|
| Authentication | `JwtAuthenticationFilter` — validates Bearer tokens, propagates claims |
| Tracing | `CorrelationIdFilter` — generates / propagates `X-Correlation-Id` |
| Logging | `RequestResponseLoggingFilter` — structured request/response logging |
| Circuit Breaker | Resilience4j — 50% failure threshold, 10-call sliding window |
| Rate Limiting | Redis-backed per-user `KeyResolver` |
| Routing | Declarative (YAML) + Programmatic (`ProgrammaticRouteConfig`) |

---

### 6. Circuit Breaker
**Domain:** Banking / Payment Gateway

Prevents cascading failures when an external payment gateway is unreliable.

```
CLOSED ──(≥50% fail in 10 calls)──► OPEN ──(30s wait)──► HALF_OPEN ──► CLOSED / OPEN
```

| Configuration | Value |
|---------------|-------|
| Failure rate threshold | 50% |
| Sliding window size | 10 calls |
| Wait in open state | 30 seconds |
| Half-open probe calls | 3 |
| Fallback response | `PaymentResponse(status=PENDING, "queued for retry")` |

---

### 7. Bulkhead
**Domain:** Financial / Trading Platform

Isolates the risk-assessment path with a Resilience4j semaphore. When full, new trades are rejected immediately instead of queuing indefinitely.

```java
TradeRequest trade = TradeRequest.buy("TRADER-01", "AAPL", 10, new BigDecimal("150.00"));
TradeResult result = tradingBulkheadService.executeTrade(trade);
// → EXECUTED (within £1M risk limit)
```

| Configuration | Value |
|---------------|-------|
| Max concurrent calls | 10 |
| Max wait duration | 500ms |
| Risk limit | £1,000,000 (price × quantity) |

---

### 8. Sidecar
**Domain:** eCommerce / Product Catalogue

Cross-cutting concerns (logging, metrics) run in a co-located sidecar class — mirroring Envoy/Linkerd in a Kubernetes pod. The `ProductService` contains zero logging or metrics code.

| Sidecar | Responsibility |
|---------|----------------|
| `LoggingSidecar` | Structured request / response / error logging via SLF4J |
| `SidecarMetrics` | Thread-safe counters: totalRequests, errors, avgLatencyMs, errorRate |

---

### 9. Event-Driven
**Domain:** Payment / Banking

Publishers emit sealed domain events; handlers react autonomously. New consumers added with zero changes to the publisher.

| Event | Publisher | Consumers |
|-------|----------|-----------|
| `PaymentInitiated` | `PaymentService` | `FraudDetectionHandler` (flags ≥ £10,000) |
| `PaymentCompleted` | `PaymentService` | `NotificationHandler` |
| `PaymentFailed` | `PaymentService` | `NotificationHandler` |

---

### 10. Strangler Fig
**Domain:** Banking / Account Migration

Migrates a monolith to microservices incrementally. A facade routes each operation to legacy or modern implementation via feature toggles — zero downtime.

| Phase | createAccount | findAccount | updateBalance |
|-------|:---:|:---:|:---:|
| 0 — Start | LEGACY | LEGACY | LEGACY |
| 1 — Create migrated | **MODERN** | LEGACY | LEGACY |
| 2 — Read migrated | MODERN | **MODERN** | LEGACY |
| 3 — Full migration | MODERN | MODERN | **MODERN** |
| 4 — Delete legacy | ✓ | ✓ | ✓ |

---

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 (sealed types, records, preview features) |
| Framework | Spring Boot | 3.4.2 |
| Reactive Gateway | Spring Cloud Gateway (WebFlux/Netty) | Spring Cloud 2024.0.0 |
| Resilience | Resilience4j (CircuitBreaker, Bulkhead) | via Spring Cloud BOM |
| Security | Spring Security + OAuth2 Resource Server | — |
| Build | Gradle Wrapper | 9.x |
| Code Generation | Lombok | — |
| Testing | JUnit 5, Mockito, WebTestClient, StepVerifier | — |
| Coverage | JaCoCo | — |

> **Production targets (not wired in tests):** PostgreSQL, Apache Kafka, Redis, EventStoreDB.

---

## Project Structure

```
microservice-patterns/
├── src/
│   ├── main/
│   │   ├── java/…/designpattern/
│   │   │   ├── saga/                      # Saga Orchestration + Choreography
│   │   │   │   ├── CampaignOrchestrator.java
│   │   │   │   ├── common/                # OrchestratorState, LogMessage
│   │   │   │   ├── choreography/          # UserServiceChoreography
│   │   │   │   └── ecommerce/             # OrderSagaOrchestrator, SagaStep, services
│   │   │   ├── cqrs/banking/              # CQRS + Event Sourcing
│   │   │   │   ├── api/                   # REST controllers (command + query)
│   │   │   │   ├── command/               # Sealed AccountCommand types
│   │   │   │   ├── domain/                # Account aggregate
│   │   │   │   ├── event/                 # Sealed AccountEvent types
│   │   │   │   ├── eventstore/            # EventStore interface + InMemoryEventStore
│   │   │   │   ├── projection/            # AccountProjection (event → read model)
│   │   │   │   ├── readmodel/             # AccountView, TransactionView
│   │   │   │   ├── repository/            # In-memory read/write repositories
│   │   │   │   └── service/               # AccountCommandService, AccountQueryService
│   │   │   ├── gateway/                   # API Gateway (WebFlux)
│   │   │   │   ├── config/                # Security, routes, circuit breaker, rate limiter
│   │   │   │   ├── controller/            # FallbackController
│   │   │   │   └── filter/                # JWT, CorrelationId, Logging filters
│   │   │   ├── circuitbreaker/payment/    # Circuit Breaker — banking payment
│   │   │   ├── bulkhead/trading/          # Bulkhead — financial trading
│   │   │   ├── sidecar/ecommerce/         # Sidecar — eCommerce product catalogue
│   │   │   ├── eventdriven/payment/       # Event-Driven — payment/banking
│   │   │   └── strangler/banking/         # Strangler Fig — banking migration
│   │   └── resources/
│   │       ├── application.yaml           # Gateway routes, security config
│   │       └── docs/                      # PlantUML diagrams + developer guides
│   └── test/
│       ├── java/…/designpattern/          # 160 TDD tests (3 positive + 3 negative each)
│       └── resources/
│           └── application.yaml           # Test overrides: empty routes, mock JWT, no Redis
├── CLAUDE.md                              # AI assistant project context
├── DEVELOPER-TESTING-GUIDE.md            # Detailed local testing guide
├── README.md                              # This file
└── build.gradle
```

---

## Getting Started

### Prerequisites

| Requirement | Minimum Version |
|-------------|----------------|
| JDK | 17 |
| Git | any |

The Gradle wrapper is bundled — no separate Gradle install needed.

### Clone & Build

```bash
git clone https://github.com/saurabhshcs/microservices-design-patterns.git
cd microservices-design-patterns
./gradlew build
```

---

## Running Tests

### All tests

```bash
./gradlew test
```

### Run by pattern

```bash
./gradlew test --tests "*.saga.*"           # Saga (orchestration + choreography)
./gradlew test --tests "*.cqrs.*"           # CQRS + EventStore
./gradlew test --tests "*.gateway.*"        # API Gateway
./gradlew test --tests "*.circuitbreaker.*" # Circuit Breaker
./gradlew test --tests "*.bulkhead.*"       # Bulkhead
./gradlew test --tests "*.sidecar.*"        # Sidecar
./gradlew test --tests "*.eventdriven.*"    # Event-Driven
./gradlew test --tests "*.strangler.*"      # Strangler Fig
```

### Run a single test class

```bash
./gradlew test --tests "*.PaymentCircuitBreakerServiceTest"
```

### View HTML report

```bash
open build/reports/tests/test/index.html
```

For troubleshooting and advanced options see [DEVELOPER-TESTING-GUIDE.md](DEVELOPER-TESTING-GUIDE.md).

---

## Test Coverage

| Metric | Result |
|--------|--------|
| **Total tests** | **160 / 160 passing** |
| **Line coverage** | **99%** |
| **Branch coverage** | **100%** |
| **Class coverage** | **100%** |

Every test class follows the **TDD convention: 3 positive + 3 negative scenarios**.

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## Architecture Diagrams

PlantUML diagrams for each pattern are in `src/main/resources/docs/`:

| Pattern | Diagram |
|---------|---------|
| Circuit Breaker | [`circuit-breaker-pattern.puml`](src/main/resources/docs/circuit-breaker-pattern.puml) |
| Bulkhead | [`bulkhead-pattern.puml`](src/main/resources/docs/bulkhead-pattern.puml) |
| Sidecar | [`sidecar-pattern.puml`](src/main/resources/docs/sidecar-pattern.puml) |
| Event-Driven | [`event-driven-pattern.puml`](src/main/resources/docs/event-driven-pattern.puml) |
| Strangler Fig | [`strangler-pattern.puml`](src/main/resources/docs/strangler-pattern.puml) |

Render with the [PlantUML](https://plantuml.com/) CLI or the IntelliJ IDEA / VS Code PlantUML plugin.

---

## Confluence Documentation

Full documentation with architecture diagrams, implementation walkthroughs, and test breakdowns:

| Pattern | Confluence Page |
|---------|----------------|
| **All Patterns (Parent)** | [Microservice Design Patterns — Java & Spring Boot Training](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/2326530) |
| Circuit Breaker | [Page #6357006](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/6357006) |
| Bulkhead | [Page #6357029](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/6357029) |
| Sidecar | [Page #6357052](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/6357052) |
| Event-Driven | [Page #6455298](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/6455298) |
| Strangler Fig | [Page #6488066](https://saurabhshcs.atlassian.net/wiki/spaces/TECH/pages/6488066) |

Each page contains 9 sections: overview · domain scenario · PlantUML diagram · class responsibilities · full Java source · TDD test breakdown · configuration reference · run commands · GitHub PR link.

---

## Pattern Quick Reference

| Pattern | Package | Domain | Key Classes | PR |
|---------|---------|--------|-------------|-----|
| Saga Orchestration (ad-tech) | `saga/` | Ad-tech | `CampaignOrchestrator`, `OrchestratorState` | — |
| Saga Orchestration (eCommerce) | `saga/ecommerce/` | eCommerce | `OrderSagaOrchestrator`, `SagaStep` | — |
| Saga Choreography | `saga/choreography/` | Ad-tech | `UserServiceChoreography` | — |
| CQRS + Event Sourcing | `cqrs/banking/` | Banking | `AccountCommandService`, `AccountProjection` | — |
| EventStore | `cqrs/banking/eventstore/` | Banking | `EventStore`, `InMemoryEventStore` | [#13](https://github.com/saurabhshcs/microservices-design-patterns/pull/13) |
| API Gateway | `gateway/` | RetailHub | `JwtAuthenticationFilter`, `ProgrammaticRouteConfig` | — |
| Circuit Breaker | `circuitbreaker/` | Banking/Payment | `PaymentCircuitBreakerService`, `PaymentGatewayClient` | [#16](https://github.com/saurabhshcs/microservices-design-patterns/pull/16) |
| Bulkhead | `bulkhead/` | Financial/Trading | `TradingBulkheadService`, `RiskAssessmentService` | [#17](https://github.com/saurabhshcs/microservices-design-patterns/pull/17) |
| Sidecar | `sidecar/` | eCommerce | `ProductService`, `LoggingSidecar`, `SidecarMetrics` | [#18](https://github.com/saurabhshcs/microservices-design-patterns/pull/18) |
| Event-Driven | `eventdriven/` | Payment/Banking | `PaymentService`, `FraudDetectionHandler`, `NotificationHandler` | [#19](https://github.com/saurabhshcs/microservices-design-patterns/pull/19) |
| Strangler Fig | `strangler/` | Banking | `StranglerFacade`, `FeatureToggle` | [#20](https://github.com/saurabhshcs/microservices-design-patterns/pull/20) |

---

## Contributing

Contributions are welcome:

- Open an issue to suggest a new pattern
- Submit a PR with a new pattern — include TDD tests (3 positive + 3 negative) and a PlantUML diagram
- Improve existing tests or documentation

---

## Author

**Saurabh Sharma** — Software engineer focused on distributed systems, microservices architecture, and cloud-native Java.

- GitHub: [@saurabhshcs](https://github.com/saurabhshcs)
- LinkedIn: [saurabhshcs](https://www.linkedin.com/in/saurabhshcs)
- Repository: [microservices-design-patterns](https://github.com/saurabhshcs/microservices-design-patterns)

---

## License

This project is for educational purposes.
