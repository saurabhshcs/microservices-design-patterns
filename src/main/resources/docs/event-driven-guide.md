# Event-Driven Pattern — Developer Guide

## Overview
Services communicate via domain events rather than direct method calls.
Publishers and subscribers are fully decoupled — new consumers can be added
without modifying the publisher.

**Domain:** Payment / Banking
**Transport:** Spring `ApplicationEventPublisher` (swap for Kafka in production)

## Event Types (Sealed Interface)
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `PaymentInitiated` | Payment started | `FraudDetectionHandler` |
| `PaymentCompleted` | Payment succeeded | `NotificationHandler` |
| `PaymentFailed` | Payment declined | `NotificationHandler` |

## Running the Tests
```bash
./gradlew test --tests "*.eventdriven.*"
```

## Key Classes
| Class | Role |
|-------|------|
| `PaymentService` | Publishes events; no direct handler dependencies |
| `PaymentEventPublisher` | Thin wrapper over `ApplicationEventPublisher` |
| `FraudDetectionHandler` | Reacts to `PaymentInitiated`; flags high-value payments |
| `NotificationHandler` | Reacts to `PaymentCompleted`/`PaymentFailed`; sends notifications |
