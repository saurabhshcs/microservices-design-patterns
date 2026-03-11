# Developer Testing Guide

This guide explains how to run, filter, and interpret the test suite for the
**Microservice Design Patterns** training project locally.

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java (JDK) | 17 or higher |
| Gradle | Use the included wrapper (`./gradlew`) — no separate install needed |
| RAM | 2 GB minimum (Spring context tests load Netty/WebFlux) |

Verify your Java version:
```bash
java -version
# java version "17.x.x" ...
```

---

## Running All Tests

```bash
./gradlew test
```

Compiles sources, runs all 154 tests, and generates a JaCoCo coverage report.

---

## Running a Single Test Class

```bash
./gradlew test --tests "com.saurabhshcs.adtech.microservices.designpattern.saga.ecommerce.api.OrderControllerExtendedTest"
```

```bash
# Shorter form using simple class name (works when names are unique)
./gradlew test --tests "*.OrderControllerExtendedTest"
```

---

## Running a Single Test Method

```bash
./gradlew test --tests "*.OrderControllerExtendedTest.createOrder_withMinimumBoundaryValues_returns201"
```

---

## Running Tests by Package / Pattern

```bash
# All CQRS banking tests
./gradlew test --tests "*.cqrs.banking.*"

# All saga ecommerce tests
./gradlew test --tests "*.saga.ecommerce.*"

# All gateway tests
./gradlew test --tests "*.gateway.*"
```

---

## Viewing Test Reports

### HTML Report (human-readable)
```bash
open build/reports/tests/test/index.html
```

### JaCoCo Coverage Report (HTML)
```bash
open build/reports/jacoco/test/html/index.html
```

### JaCoCo Coverage Report (XML — for CI tools)
```
build/reports/jacoco/test/jacocoTestReport.xml
```

---

## Test Structure

Each test class follows **TDD conventions** with clearly labelled sections:

```
ClassName
├── Positive scenarios (3 tests)  ← happy-path, boundary values, expected output
└── Negative scenarios (3 tests)  ← invalid input, failure states, exception paths
```

### Key Test Classes

| Pattern | Test Class | What it covers |
|---------|-----------|----------------|
| Saga | `OrderControllerTest` | Basic create-order HTTP responses |
| Saga | `OrderControllerExtendedTest` | Boundary values, all failure statuses, response body |
| Saga | `PaymentServiceTest` | Payment limit, compensation |
| Saga | `InventoryServiceTest` | Stock reservation, compensation |
| Saga | `ShippingServiceTest` | Scheduling, compensation |
| Saga | `OrchestratorStateTest` | State transitions, terminal detection |
| CQRS | `AccountCommandServiceExtendedTest` | Command handling, error paths |
| CQRS | `AccountDomainTest` | Aggregate rules (deposit, withdraw, transfer) |
| CQRS | `AccountProjectionExtendedTest` | Event-driven read-model updates |
| CQRS | `InMemoryEventStoreTest` | Append, replay, partial replay |
| Gateway | `JwtAuthenticationFilterTest` | Token validation, whitelisting |
| Gateway | `CorrelationIdFilterTest` | ID propagation and generation |
| Gateway | `CircuitBreakerConfigurationTest` | Failure thresholds, factory wiring |
| Gateway | `SecurityConfigTest` | CORS config, JWT decoder |
| Gateway | `ProgrammaticRouteConfigTest` | Route registration (integration) |

---

## Running Tests for the `OrderController` Specifically

```bash
# Original test class
./gradlew test --tests "*.OrderControllerTest"

# Extended test class (boundary values, failure body assertions)
./gradlew test --tests "*.OrderControllerExtendedTest"

# Both together
./gradlew test --tests "*.saga.ecommerce.api.*"
```

---

## Clean Build

If tests behave unexpectedly (stale bytecode), run a full clean:

```bash
./gradlew clean test
```

---

## Common Troubleshooting

### `BeanDefinitionOverrideException` during gateway tests
Ensure `src/test/resources/application.yaml` contains:
```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

### `RouteDefinitionRouteLocator` / Redis errors
The same `src/test/resources/application.yaml` disables Redis-backed routes for tests:
```yaml
spring:
  cloud:
    gateway:
      routes: []
```

### `UnsatisfiedDependencyException: JwtDecoder`
`ProgrammaticRouteConfigTest` uses `@MockBean JwtDecoder` — ensure the test class has this annotation.

### Java preview features warning
The project uses Java 17 sealed types with `--enable-preview`. The compiler warning
```
Note: Some input files use preview features of Java SE 17.
```
is expected and does not affect test execution.

### `jacocoTestReport` fails when running a single test class
JaCoCo requires data from all tests to generate a valid report. Either run:
```bash
./gradlew test -x jacocoTestReport --tests "*.TargetTest"
```
or run the full suite to generate the report:
```bash
./gradlew test
```

---

## Coverage Thresholds

| Metric | Current |
|--------|---------|
| Line | ≥ 99% |
| Branch | 100% |
| Class | 100% |

To verify coverage locally:
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```
