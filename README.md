# Microservices Design Patterns

A hands-on training project demonstrating key microservices design patterns implemented in Java with Spring Boot. This repository serves as a learning resource for developers exploring distributed systems and microservices architecture.

## Project Overview

This project explores key microservices design patterns implemented in Java. Using real-world domains (ad-tech campaign orchestration and eCommerce order management), it demonstrates how to coordinate multi-step business workflows with proper patterns for consistency, scalability, and maintainability.

### Patterns Covered

| Pattern | Status | Description | Docs |
|---------|--------|-------------|------|
| Saga — Orchestration | Implemented | Central orchestrator controls the workflow and compensation | [docs/orchestration](./docs/orchestration/) |
| Saga — Choreography | In Progress | Services react to events and coordinate without a central controller | [docs/choreography](./docs/choreography/) |
| CQRS | Planned | Separate read and write models for scalable, optimised data access | [docs/cqrs](./docs/cqrs/) |

### Domain Context

The examples use an **ad-tech domain**, modelling a campaign launch workflow with three steps:

1. **Budget Validation** — Confirm the campaign budget is approved
2. **Inventory Reservation** — Reserve ad slots/inventory for the campaign
3. **Scheduling** — Schedule the campaign for delivery
4. **Compensation** — On any failure, undo completed steps (cancel inventory, release budget, etc.)

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.0 |
| Build Tool | Gradle (Wrapper included) |
| Utilities | Lombok |
| Testing | JUnit 5, Spring Boot Test |
| Logging | SLF4J + Logback |

## Project Structure

```
src/
├── main/java/com/saurabhshcs/adtech/microservices/designpattern/
│   ├── model/
│   │   └── UserModel.java                   # Domain model using Lombok builder
│   └── saga/
│       ├── CampaignOrchestrator.java         # Saga orchestrator with compensation logic
│       ├── common/
│       │   ├── OrchestratorState.java        # State machine enum
│       │   └── LogMessage.java               # Standardised log message templates
│       ├── orchestrator/
│       │   └── UserServiceOrchestrator.java  # Entry point stub
│       └── choreography/
│           └── UserServiceChoreography.java  # Choreography pattern (in progress)
└── test/java/com/saurabhshcs/adtech/microservices/designpattern/
    └── saga/
        ├── CampaignOrchestratorTest.java     # Unit tests for the orchestrator
        └── common/
            └── TestLogConstants.java         # Shared test constants
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
