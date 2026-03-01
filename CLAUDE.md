# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew build          # Compile and run all tests
./gradlew test           # Run tests only
./gradlew bootRun        # Start the application (API Gateway on port 8080)
./gradlew clean build    # Full clean rebuild
```

Run a single test class:
```bash
./gradlew test --tests "com.saurabhshcs.adtech.microservices.designpattern.saga.CampaignOrchestratorTest"
```

Test reports: `build/reports/tests/test/index.html`

**Requirements:** Java 17+, Gradle wrapper included.

## Architecture Overview

This is an **educational single-module Spring Boot project** demonstrating 5 microservice design patterns in one codebase. All patterns share the same `build.gradle` but live in separate packages under `com.saurabhshcs.adtech.microservices.designpattern`.

**Stack:** Java 17, Spring Boot 3.4.2, Spring Cloud 2024.0.0, Spring Cloud Gateway (reactive/WebFlux), Resilience4j, Spring Security/OAuth2, Lombok.

### Pattern Packages

| Package | Pattern | Key Classes |
|---------|---------|-------------|
| `saga/` | Saga Orchestration & Choreography | `CampaignOrchestrator`, `OrderSagaOrchestrator`, `UserServiceChoreography` |
| `cqrs/` | CQRS + Event Sourcing | `AccountCommandService`, `AccountQueryService`, `AccountProjection` |
| `gateway/` | API Gateway | `JwtAuthenticationFilter`, `CircuitBreakerConfiguration`, `ProgrammaticRouteConfig` |

Documentation for each pattern: `src/main/resources/docs/`

### Saga Pattern

Two sub-implementations coexist:

1. **Ad-tech orchestration** (`saga/`) — Linear state machine (`OrchestratorState`: STARTED → BUDGET_VALIDATED → INVENTORY_RESERVED → SCHEDULED → COMPLETED/FAILED). `CampaignOrchestrator` drives a 3-step campaign launch (budget validation, inventory reservation, scheduling) with explicit compensation on failure.

2. **E-commerce orchestration** (`saga/ecommerce/`) — Services implement `SagaStep<Order>` interface. `OrderSagaOrchestrator` chains Payment → Inventory → Shipping; compensation runs in LIFO order on failure.

3. **Choreography** (`saga/choreography/`) — Event-driven; services react autonomously without a central controller.

### CQRS

Domain: Banking account management. Uses Java 17 **sealed types** for commands and events.

- **Write side:** `AccountCommandService` executes commands on the `Account` aggregate, publishes domain events via Spring `ApplicationEventPublisher`.
- **Read side:** `AccountProjection` listens to events and updates `AccountView`/`TransactionView` in `InMemoryAccountReadRepository`.
- **Repositories are in-memory** (demo only; intended to be replaced with JPA/PostgreSQL).

### API Gateway

Reactive stack (WebFlux/Netty). Routes configured declaratively in `application.yaml` or programmatically in `ProgrammaticRouteConfig`.

Key filters (applied globally or per-route):
- `JwtAuthenticationFilter` — validates Bearer tokens, extracts claims
- `CorrelationIdFilter` — propagates/generates `X-Correlation-Id`
- `RequestResponseLoggingFilter` — structured request/response logging

Circuit breaker: Resilience4j with 50% failure threshold, 10-call sliding window, 30s recovery. Rate limiting: Redis-backed per-user. Fallback responses in `FallbackController`.

### Important Architectural Constraints

- **All patterns are in one module** — this is intentional for training purposes. Do not split into multi-module without clear reason.
- **The gateway uses WebFlux** (reactive). Do not introduce blocking servlet APIs (`@EnableWebMvc`, `spring-boot-starter-web`) — they conflict with WebFlux.
- **No live infrastructure required for tests** — tests use in-memory repositories and mocks. Kafka/PostgreSQL/Redis are documented for production use but not wired in tests.
- The `CqrsBankingApplication` is the only `@SpringBootApplication` class (in `cqrs/` package). Other patterns are exercised through tests, not as standalone apps.