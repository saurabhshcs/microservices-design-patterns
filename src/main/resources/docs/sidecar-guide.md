# Sidecar Pattern — Developer Guide

## Overview
The Sidecar pattern deploys a helper process (the sidecar) alongside the main service
in the same pod/process. The sidecar handles cross-cutting concerns so the main
service stays focused on business logic.

**Domain:** eCommerce / Product Catalogue
**Real-world analogy:** Envoy proxy in Istio service mesh

## What the Sidecar Handles
| Concern | Sidecar Class |
|---------|--------------|
| Structured logging | `LoggingSidecar` |
| Request/latency metrics | `SidecarMetrics` |

## Running the Tests
```bash
./gradlew test --tests "*.sidecar.*"
```

## Key Classes
| Class | Role |
|-------|------|
| `ProductService` | Main service — pure business logic |
| `LoggingSidecar` | Cross-cutting: structured log output |
| `SidecarMetrics` | Cross-cutting: request counts, latency, error rate |
| `ProductRequest/Response` | Immutable value objects (records) |
