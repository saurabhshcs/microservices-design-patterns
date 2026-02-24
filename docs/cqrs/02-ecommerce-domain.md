# eCommerce Domain Model

## Bounded Context: Order Management

This guide uses the **Order Management** bounded context of an eCommerce platform. This context is responsible for placing, tracking, and managing customer orders.

---

## Aggregates

An **aggregate** is the consistency boundary on the write side. All business rule enforcement happens inside the aggregate.

### Order Aggregate (Root)

The `Order` aggregate is the central write-model entity. It owns all invariants around order lifecycle.

```
Order (Aggregate Root)
├── orderId: UUID                   ← Aggregate identity
├── customerId: UUID
├── status: OrderStatus             ← PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED
├── items: List<OrderItem>          ← Value object collection
├── shippingAddress: Address        ← Value object
├── totalAmount: Money              ← Value object
├── placedAt: Instant
└── updatedAt: Instant
```

**Business Invariants enforced by the aggregate:**
- An order cannot be cancelled once it has shipped
- An order must have at least one item
- Total amount must be positive
- A shipped order cannot have its address changed

### Product Aggregate

```
Product (Aggregate Root)
├── productId: UUID
├── name: String
├── description: String
├── price: Money
├── stockQuantity: int
└── category: ProductCategory
```

### Customer Aggregate

```
Customer (Aggregate Root)
├── customerId: UUID
├── email: EmailAddress             ← Value object (validated format)
├── fullName: PersonName            ← Value object
├── addresses: List<Address>
└── loyaltyPoints: int
```

---

## Entities

Entities have identity but do not act as aggregate roots within the Order context:

### OrderItem (Entity within Order)

```
OrderItem
├── orderItemId: UUID
├── productId: UUID
├── productName: String             ← Snapshot at time of order
├── unitPrice: Money                ← Snapshot at time of order
├── quantity: int
└── subtotal: Money                 ← Derived: unitPrice × quantity
```

> Prices and names are **snapshotted** at order time — they do not change if the product catalogue is updated later.

---

## Value Objects

Value objects have no identity. They are defined by their attributes and are immutable.

### Money

```java
// Java 21 record — immutable by design
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

### Address

```java
public record Address(
    String street,
    String city,
    String state,
    String postalCode,
    String countryCode
) {}
```

### EmailAddress

```java
public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public EmailAddress {
        Objects.requireNonNull(value, "email must not be null");
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
    }
}
```

---

## Enumerations

### OrderStatus

```java
public enum OrderStatus {
    PENDING,        // Order placed, awaiting payment confirmation
    CONFIRMED,      // Payment confirmed, being prepared
    SHIPPED,        // Handed to carrier
    DELIVERED,      // Delivered to customer
    CANCELLED       // Cancelled before shipping
}
```

---

## Domain Events

Each state transition in the Order aggregate emits a domain event. These events drive the read model updates.

| Event | Triggered By | Payload |
|-------|-------------|---------|
| `OrderPlacedEvent` | `PlaceOrderCommand` | orderId, customerId, items, totalAmount, shippingAddress |
| `OrderConfirmedEvent` | Payment success callback | orderId, confirmedAt |
| `OrderShippedEvent` | `MarkOrderShippedCommand` | orderId, trackingNumber, shippedAt |
| `OrderDeliveredEvent` | Carrier callback | orderId, deliveredAt |
| `OrderCancelledEvent` | `CancelOrderCommand` | orderId, reason, cancelledAt |
| `ShippingAddressUpdatedEvent` | `UpdateShippingAddressCommand` | orderId, newAddress |

> See [05-events.md](./05-events.md) for full event definitions and projection logic.

---

## Domain Model Diagram

```
┌──────────────────────────────────────────────┐
│              Order (Aggregate Root)           │
│                                              │
│  orderId: UUID                               │
│  customerId: UUID ──────────────────────────►│ Customer Aggregate
│  status: OrderStatus                         │ (separate bounded context)
│  shippingAddress: Address ──┐                │
│  totalAmount: Money         │ Value Objects  │
│  placedAt: Instant          │                │
│                             ▼                │
│  items: List<OrderItem>                      │
│    └── OrderItem                             │
│         ├── orderItemId: UUID                │
│         ├── productId: UUID ────────────────►│ Product Aggregate
│         ├── productName: String (snapshot)   │ (separate bounded context)
│         ├── unitPrice: Money (snapshot)      │
│         └── quantity: int                    │
└──────────────────────────────────────────────┘
```

---

## Read Model Projections

The **read side** uses denormalised views optimised for specific queries:

### OrderSummaryView (for list views)

```
OrderSummaryView
├── orderId: UUID
├── customerName: String          ← Joined from customer data
├── status: String
├── totalAmount: String           ← Formatted (e.g., "£49.99")
├── itemCount: int
└── placedAt: String              ← Human-readable date
```

### OrderDetailView (for single order page)

```
OrderDetailView
├── orderId: UUID
├── customerId: UUID
├── customerName: String
├── customerEmail: String
├── status: String
├── items: List<OrderItemView>
│   ├── productName
│   ├── unitPrice
│   ├── quantity
│   └── subtotal
├── shippingAddress: AddressView
├── totalAmount: String
├── placedAt: String
└── trackingNumber: String (nullable)
```

### CustomerOrderHistoryView (for "My Orders" page)

```
CustomerOrderHistoryView
├── customerId: UUID
└── orders: List<OrderSummaryView>  ← Pre-aggregated, most recent first
```

> See [04-queries.md](./04-queries.md) for query handlers that use these read models.
