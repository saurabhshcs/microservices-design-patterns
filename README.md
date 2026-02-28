# Microservices Design Patterns

A hands-on training project demonstrating key microservices design patterns implemented in Java with Spring Boot. This repository serves as a learning resource for developers exploring distributed systems and microservices architecture.

## Project Overview

This project explores foundational **microservice design patterns** implemented in Java with Spring Boot. It covers five patterns across two categories:

- **Distributed Transaction Patterns:** Saga (Orchestration and Choreography) for managing multi-step business workflows with compensation logic.
- **Architecture Patterns:** CQRS (Command Query Responsibility Segregation), Transactional Outbox, and API Gateway for building scalable, reliable, and maintainable microservice architectures.

Each pattern includes a real-world scenario, complete Java implementation, dependency justifications, and database schemas where applicable.

### Patterns Covered

| Pattern | Status | Description | Documentation |
|---------|--------|-------------|---------------|
| Saga -- Orchestration | Implemented | Central orchestrator controls the workflow and compensation | [src/main/resources/docs/saga/orchestration/](src/main/resources/docs/saga/orchestration/) |
| Saga -- Choreography | In Progress | Services react to events and coordinate without a central controller | [src/main/resources/docs/saga/choreography/](src/main/resources/docs/saga/choreography/) |
| CQRS | Documented | Separate read and write models for independent scaling and optimization | [src/main/resources/docs/cqrs/](src/main/resources/docs/cqrs/) |
| Transactional Outbox | Documented | Reliable event publishing without distributed transactions | [src/main/resources/docs/transactional-outboxing/](src/main/resources/docs/transactional-outboxing/) |
| API Gateway | Documented | Unified entry point with authentication, rate limiting, and circuit breaking | [src/main/resources/docs/api-gateway/](src/main/resources/docs/api-gateway/) |

### Pattern Selection Guide

For guidance on which pattern to apply and when to combine patterns, see the [Pattern Selection Summary](src/main/resources/docs/summary.md).

### Domain Context

The examples use an **ad-tech domain**, modelling a campaign launch workflow with three steps:

1. **Budget Validation** — Confirm the campaign budget is approved
2. **Inventory Reservation** — Reserve ad slots/inventory for the campaign
3. **Scheduling** — Schedule the campaign for delivery
4. **Compensation** — On any failure, undo completed steps (cancel inventory, release budget, etc.)

## Technology Stack

| Component | Technology | Used By |
|-----------|------------|---------|
| Language | Java 17 | All patterns |
| Framework | Spring Boot 4.0.0 (Jakarta EE 10) | All patterns |
| Gateway | Spring Cloud Gateway 5.0.x | API Gateway |
| ORM | Spring Data JPA (Hibernate) | CQRS, Outbox |
| Database | PostgreSQL 15+ | CQRS, Outbox |
| Message Broker | Apache Kafka 3.7+ | Outbox |
| Cache / Rate Limiting | Redis 7.x | API Gateway |
| Circuit Breaker | Resilience4j 2.2.x | API Gateway |
| Security | Spring Security (JWT / OAuth2) | API Gateway |
| Build Tool | Gradle (Wrapper included) | All patterns |
| Utilities | Lombok | All patterns |
| Testing | JUnit 5, Spring Boot Test, Testcontainers | All patterns |
| Logging | SLF4J + Logback | All patterns |

## Project Structure

```
.
├── src/
│   ├── main/
│   │   ├── java/com/saurabhshcs/adtech/microservices/designpattern/
│   │   │   ├── model/
│   │   │   │   └── UserModel.java
│   │   │   ├── saga/
│   │   │   │   ├── CampaignOrchestrator.java
│   │   │   │   ├── common/
│   │   │   │   ├── orchestrator/
│   │   │   │   └── choreography/
│   │   │   └── cqrs/
│   │   │       ├── CqrsBankingApplication.java
│   │   │       └── banking/
│   │   │           ├── api/
│   │   │           ├── command/
│   │   │           ├── domain/
│   │   │           ├── event/
│   │   │           ├── projection/
│   │   │           ├── readmodel/
│   │   │           ├── repository/
│   │   │           └── service/
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── docs/                         # All pattern documentation
│   │           ├── cqrs/                     # CQRS pattern (ShopStream scenario)
│   │           │   ├── README.md
│   │           │   ├── scenario.md
│   │           │   ├── implementation.md
│   │           │   └── dependencies.md
│   │           ├── transactional-outboxing/  # Outbox pattern (FinFlow scenario)
│   │           │   ├── README.md
│   │           │   ├── scenario.md
│   │           │   ├── implementation.md
│   │           │   └── dependencies.md
│   │           ├── api-gateway/              # API Gateway pattern (RetailHub scenario)
│   │           │   ├── README.md
│   │           │   ├── scenario.md
│   │           │   ├── implementation.md
│   │           │   └── dependencies.md
│   │           ├── saga/
│   │           │   ├── orchestration/        # Saga orchestration diagrams
│   │           │   └── choreography/         # Saga choreography diagrams
│   │           ├── patterns/
│   │           │   └── cqrs/diagrams/        # PlantUML diagrams
│   │           ├── linkedin-posts/
│   │           ├── summary.md                # Pattern selection guide
│   │           └── confluence-publishing.md  # Confluence publishing instructions
│   └── test/java/com/saurabhshcs/adtech/microservices/designpattern/
│       ├── saga/
│       └── cqrs/banking/
│
└── build.gradle                              # Project build configuration
```

## Getting Started

### Prerequisites

- **Java 17** or higher — [Download JDK from Adoptium](https://adoptium.net/)
- **Git**

> No need to install Gradle separately. The Gradle Wrapper (`gradlew`) bundled in the repository downloads and manages the correct Gradle version automatically.

### Clone the Repository

```bash
git clone https://github.com/saurabhshcs/microservices-design-patterns.git
cd microservices-design-patterns
```

### Build the Project

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

This compiles all source code, runs annotation processing (Lombok), and executes the full test suite. Build artefacts are placed in `build/`.

### Run the Tests

```bash
# Linux / macOS
./gradlew test

# Windows
gradlew.bat test
```

An HTML test report is generated at `build/reports/tests/test/index.html`. Open it in a browser to see detailed results.

### Run the Application

```bash
# Linux / macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

> **Note:** This is a training project focused on design patterns through unit-tested code. The application has no HTTP endpoints; the patterns are exercised via the test suite.

## Key Concepts

### The Saga Pattern

The **Saga Pattern** solves the problem of maintaining data consistency across multiple microservices without distributed transactions (two-phase commit). A saga is a sequence of local transactions where each step either completes successfully or triggers compensating transactions to undo prior steps.

**Two coordination styles:**

- **Orchestration** — A central orchestrator directs each service to perform its step. If a step fails, the orchestrator explicitly calls compensating actions on previous steps.
- **Choreography** — Each service publishes events after completing its step. Other services react to those events autonomously, with no central controller.

### State Machine

The `CampaignOrchestrator` implements the orchestration style as a linear state machine:

```
STARTED → BUDGET_VALIDATED → INVENTORY_RESERVED → SCHEDULED → COMPLETED
                                                         ↓ (on any failure)
                                                      FAILED (after compensation)
```

On failure at any step, the orchestrator calls `compensate()` to reverse already-completed steps before marking the saga as `FAILED`.

### Running a Saga (Example)

```java
CampaignOrchestrator orchestrator = new CampaignOrchestrator();
UUID campaignId = UUID.randomUUID();

// All steps succeed
OrchestratorState result = orchestrator.execute(campaignId, true, true, true);
// result == OrchestratorState.COMPLETED

// Budget validation fails — compensation is triggered automatically
OrchestratorState result = orchestrator.execute(campaignId, false, true, true);
// result == OrchestratorState.FAILED
```

## Contributing

This is a training repository. Contributions are welcome:

- Open issues to suggest new patterns to implement
- Submit pull requests with additional pattern examples
- Improve test coverage or add documentation

## About the Author

**Saurabh Sharma** is a software engineer with a focus on distributed systems, microservices architecture, and cloud-native applications. This repository is part of his hands-on learning and teaching series on advanced Java and Spring Boot patterns.

- **GitHub:** [@saurabhshcs](https://github.com/saurabhshcs)
- **Repository:** [microservices-design-patterns](https://github.com/saurabhshcs/microservices-design-patterns)

Feel free to connect, raise issues, or contribute to the project!

## License

This project is for educational purposes.
