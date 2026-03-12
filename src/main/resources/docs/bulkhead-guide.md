# Bulkhead Pattern — Developer Guide

## Overview
The Bulkhead pattern isolates failures by partitioning resources (threads, connections)
into separate pools. Like watertight compartments in a ship — one flooded compartment
doesn't sink the whole vessel.

**Domain:** Financial / Trading
**Library:** Resilience4j `resilience4j-bulkhead`

## How It Works
A semaphore Bulkhead limits the number of **concurrent** calls to the risk service.
If all 10 permits are taken, new requests are rejected immediately (after 500ms wait),
preventing thread starvation in the trading engine.

## Configuration
```java
BulkheadConfig.custom()
    .maxConcurrentCalls(10)        // max concurrent risk checks
    .maxWaitDuration(Duration.ofMillis(500))  // how long to wait for a permit
    .build();
```

## Running the Tests
```bash
./gradlew test --tests "*.bulkhead.*"
```

## Key Classes
| Class | Role |
|-------|------|
| `TradingBulkheadService` | Wraps risk checks in a semaphore Bulkhead |
| `RiskAssessmentService` | Evaluates trade value against risk limits |
| `TradeRequest` / `TradeResult` | Immutable value objects (records) |
