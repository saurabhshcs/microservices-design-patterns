# Strangler Fig Pattern — Developer Guide

## Overview
The Strangler Fig pattern migrates a monolith to microservices incrementally.
A facade routes traffic to old or new implementations based on feature toggles,
allowing zero-downtime migration one operation at a time.

**Domain:** Banking / Account Management
**Named after:** The strangler fig tree that grows around and eventually replaces its host

## Migration Phases

| Phase | createAccount | findAccount | updateBalance |
|-------|--------------|-------------|---------------|
| 0 — Start | LEGACY | LEGACY | LEGACY |
| 1 — Create migrated | **MODERN** | LEGACY | LEGACY |
| 2 — Read migrated | MODERN | **MODERN** | LEGACY |
| 3 — Full migration | MODERN | MODERN | **MODERN** |
| 4 — Delete legacy | — | — | — |

## Running the Tests
```bash
./gradlew test --tests "*.strangler.*"
```

## Key Classes
| Class | Role |
|-------|------|
| `StranglerFacade` | Routes calls to legacy or modern based on feature toggle |
| `FeatureToggle` | Controls which operations use the modern service |
| `LegacyAccountService` | Existing monolith implementation |
| `ModernAccountService` | New microservice implementation |
| `AccountSummary` | Shared value object (includes `source` field) |
