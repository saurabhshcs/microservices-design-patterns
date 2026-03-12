# Circuit Breaker Pattern — Developer Guide

## Overview
The Circuit Breaker prevents cascading failures when a downstream service is unreliable.
It acts like an electrical circuit breaker: trips on repeated failures and auto-recovers.

**Domain:** Banking / Payment
**Library:** Resilience4j `resilience4j-circuitbreaker`

## States

| State | Behaviour |
|-------|-----------|
| CLOSED | All calls pass through; failures counted in sliding window |
| OPEN | Calls short-circuited; fallback returned immediately |
| HALF_OPEN | Limited probe calls; circuit closes or re-opens based on result |

## Configuration
```java
CircuitBreakerConfig.custom()
    .failureRateThreshold(50)           // open when >= 50% fail
    .slidingWindowSize(10)              // count last 10 calls
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .permittedNumberOfCallsInHalfOpenState(3)
    .recordExceptions(RuntimeException.class)
    .build();
```

## Running the Tests
```bash
./gradlew test --tests "*.circuitbreaker.*"
```

## Key Classes
| Class | Role |
|-------|------|
| `PaymentGatewayClient` | Interface for external gateway (injectable fault) |
| `PaymentCircuitBreakerService` | Wraps calls with CircuitBreaker; provides fallback |
| `PaymentRequest` / `PaymentResponse` | Immutable value objects (records) |
