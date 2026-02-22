# Saga Orchestration Pattern - eCommerce Order Management

## Overview

The **Saga Orchestration Pattern** is a distributed transaction management strategy that coordinates multiple microservices through a central **orchestrator**. Instead of a single atomic ACID transaction spanning multiple services, a saga is a sequence of local transactions, each publishing events or messages to trigger the next step. If any step fails, compensating transactions roll back the previously completed steps in **LIFO (Last In, First Out)** order.

## Problem Statement

In a microservices architecture, processing an eCommerce order requires coordinating:
- **Payment Service** – charge the customer
- **Inventory Service** – reserve stock
- **Shipping Service** – schedule delivery

A single database transaction cannot span these boundaries. We need a mechanism that:
1. Ensures all steps complete or all are rolled back
2. Handles partial failures gracefully
3. Maintains data consistency across services
4. Provides clear audit trail of what happened

## Solution: Saga Orchestration

```
┌─────────────────────────────────────────────────────────┐
│                  OrderSagaOrchestrator                    │
│                                                           │
│  STEP 1: PaymentService.execute(order)                   │
│     ↓ success → add to executedSteps (LIFO)             │
│  STEP 2: InventoryService.execute(order)                 │
│     ↓ success → add to executedSteps (LIFO)             │
│  STEP 3: ShippingService.execute(order)                  │
│     ↓ success → COMPLETED                               │
│                                                           │
│  ON FAILURE at any step:                                 │
│     performCompensation(executedSteps) → LIFO order     │
└─────────────────────────────────────────────────────────┘
```

## Architecture

### Key Abstractions

#### `SagaStep<T>` Interface
```java
public interface SagaStep<T> {
    SagaResult execute(T context);      // Forward transaction
    SagaResult compensate(T context);   // Compensating transaction
    String stepName();
}
```

#### `OrderSagaOrchestrator`
The central coordinator that:
1. Executes steps sequentially
2. Tracks completed steps in a LIFO list (prepend at index 0)
3. Triggers compensation in reverse order on failure

```java
executedSteps.add(0, step); // LIFO: last executed = first compensated
```

#### `SagaResult`
Immutable result object with success/failure status:
```java
SagaResult.success("PaymentService")
SagaResult.failure("InventoryService", "Insufficient stock for PROD-003. Available: 0")
```

## Order Status Transitions

```
PENDING
  → PAYMENT_COMPLETED (payment processed)
  → INVENTORY_RESERVED (stock reserved)
  → SHIPPING_SCHEDULED (shipment scheduled)
  → COMPLETED ✓

On failure:
  → FAILED (failure recorded)
  → COMPENSATING (rolling back)
  → COMPENSATION_COMPLETED (rollback done)
```

## Implementation Details

### PaymentService
- Validates amount ≤ $10,000
- Generates `PAY-XXXXXXXX` payment IDs
- Stores processed payments in `ConcurrentHashMap<UUID, String>`
- **Compensation**: removes payment record, marks order as PAYMENT_FAILED

### InventoryService
- Pre-loaded stock: PROD-001(100), PROD-002(25), PROD-003(0), PROD-004(5)
- Reserves exact quantity atomically
- **Compensation**: restores reserved stock to inventory

### ShippingService
- Generates `TRK-XXXXXXXX` tracking numbers
- **Compensation**: removes shipment record

## REST API

### Create Order
```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": "CUST-001",
  "productId": "PROD-001",
  "quantity": 5,
  "amount": 99.99
}
```

**Response (Success - HTTP 201):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "CUST-001",
  "productId": "PROD-001",
  "quantity": 5,
  "amount": 99.99,
  "status": "COMPLETED",
  "failureReason": null
}
```

**Response (Failure - HTTP 422):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440001",
  "customerId": "CUST-001",
  "productId": "PROD-003",
  "quantity": 1,
  "amount": 99.99,
  "status": "COMPENSATION_COMPLETED",
  "failureReason": "Step 'InventoryService' failed: Insufficient stock for PROD-003. Available: 0"
}
```

### Test Scenarios via cURL

```bash
# Successful order
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","productId":"PROD-001","quantity":5,"amount":99.99}'

# Payment limit exceeded (amount > 10000)
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","productId":"PROD-001","quantity":1,"amount":15000}'

# Out of stock (PROD-003 has 0 units)
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","productId":"PROD-003","quantity":1,"amount":99.99}'
```

## Running the Application

```bash
cd /tmp/msp-saga
./gradlew bootRun
# Server starts on http://localhost:8081
```

## Running Tests

```bash
./gradlew test
# 5 test scenarios: success, payment limit, out-of-stock, insufficient quantity, inventory reduction
```

## Test Scenarios

| Scenario | ProductId | Amount | Quantity | Expected Result |
|----------|-----------|--------|----------|-----------------|
| Happy path | PROD-001 | 99.99 | 5 | COMPLETED |
| Payment limit | PROD-001 | 15000 | 1 | COMPENSATION_COMPLETED |
| Zero stock | PROD-003 | 99.99 | 1 | COMPENSATION_COMPLETED |
| Excess quantity | PROD-002 | 99.99 | 30 | COMPENSATION_COMPLETED |
| Inventory reduction | PROD-001 | 99.99 | 10 | Stock reduces by 10 |

## Compensation: LIFO Order

The critical insight of Saga compensation:

```
Forward execution:  Payment → Inventory → Shipping
Compensation order: Shipping ← Inventory ← Payment  (LIFO)
```

```java
// In OrderSagaOrchestrator:
executedSteps.add(0, step);  // Prepend — most recent at index 0

// When compensation triggered:
for (SagaStep<Order> step : executedSteps) {
    step.compensate(order);   // Most recently executed first
}
```

If **InventoryService** fails after **PaymentService** succeeded:
1. `executedSteps = [PaymentService]` (only payment was completed)
2. Compensation iterates: `PaymentService.compensate(order)` → refunds payment

## Trade-offs

### Advantages
- ✅ Guaranteed eventual consistency across services
- ✅ No distributed lock — high availability
- ✅ Clear audit trail of all state transitions
- ✅ Central orchestrator is easy to monitor and debug
- ✅ Individual services remain stateless

### Disadvantages
- ⚠️ Orchestrator becomes a single point of failure
- ⚠️ Compensation logic adds complexity
- ⚠️ Not all operations can be compensated (e.g., sending an email)
- ⚠️ Requires idempotent compensation (if compensation fails, retry)

## When to Use Saga Orchestration

**Use when:**
- Multiple microservices must participate in a business transaction
- You need a clear view of the overall saga state (orchestrator log)
- Steps have natural compensation actions (refund, restock, cancel)
- You prefer centralized coordination over choreography

**Avoid when:**
- Simple CRUD operations (single service)
- Very high-frequency transactions where orchestrator becomes bottleneck
- Operations that cannot be compensated (use two-phase commit instead)

## Comparison: Orchestration vs Choreography

| Aspect | Orchestration | Choreography |
|--------|---------------|--------------|
| Coordination | Central orchestrator | Distributed via events |
| Visibility | Easy (single place) | Hard (trace events) |
| Coupling | Services coupled to orchestrator | Services coupled to events |
| Complexity | Orchestrator is complex | Each service is simple |
| Testing | Easy to test orchestrator | Need integration tests |
| Best for | Complex workflows | Simple event flows |

## Technology Stack

- **Java 21** - Latest LTS, records, pattern matching
- **Spring Boot 3.4.2** - Auto-configuration, IoC container
- **Lombok** - `@Data`, `@Builder`, `@RequiredArgsConstructor`
- **Spring Validation** - `@Valid`, `@NotBlank`, `@Min`
- **Spring Actuator** - Health checks at `/actuator/health`

## Project Structure

```
src/
├── main/java/com/saurabhshcs/.../saga/ecommerce/
│   ├── SagaOrchestrationApplication.java
│   ├── api/
│   │   ├── OrderController.java
│   │   └── OrderRequest.java
│   ├── domain/
│   │   ├── Order.java
│   │   └── OrderStatus.java
│   ├── orchestrator/
│   │   └── OrderSagaOrchestrator.java
│   ├── saga/
│   │   ├── SagaResult.java
│   │   └── SagaStep.java
│   └── service/
│       ├── PaymentService.java
│       ├── InventoryService.java
│       └── ShippingService.java
├── test/java/.../
│   └── OrderSagaOrchestratorTest.java
└── resources/
    └── application.yaml
patterns/saga-orchestration/
├── README.md (this file)
└── diagrams/
    ├── 01-sequence-success.puml
    ├── 02-sequence-compensation.puml
    ├── 03-system-context.puml
    ├── 04-component.puml
    └── 05-user-journey.puml
```

## References

- [Chris Richardson - Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Microsoft - Saga Distributed Transactions](https://docs.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Spring Boot 3.4.2 Documentation](https://spring.io/projects/spring-boot)
