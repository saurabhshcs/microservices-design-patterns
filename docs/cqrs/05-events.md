# Domain Events — The Bridge Between Write and Read

## What Are Domain Events?

A **Domain Event** is a record that something meaningful happened in the domain. Events are:

- Named in the **past tense** — `OrderPlaced`, `OrderCancelled` (not `PlaceOrder`)
- **Immutable** — once created, they never change
- **Facts** — they record what happened, not what should happen
- **The bridge** — they carry information from the write side to the read side

```
Write Side                                           Read Side
──────────────────────────────────────────────────────────────
Order.place() ──► OrderPlacedEvent ──► [Event Bus] ──► Projector ──► Read Store
```

---

## Domain Event Definitions (Java 21)

All domain events implement a common marker interface and use Java 21 records:

```java
// Base marker interface
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
    String aggregateType();
    UUID aggregateId();
}

// OrderPlacedEvent.java
public record OrderPlacedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID orderId,
    UUID customerId,
    List<OrderItemSnapshot> items,
    Money totalAmount,
    Address shippingAddress
) implements DomainEvent {

    // Factory method with auto-generated eventId and timestamp
    public static OrderPlacedEvent of(
            UUID orderId,
            UUID customerId,
            List<OrderItem> items,
            Money totalAmount,
            Address shippingAddress) {
        return new OrderPlacedEvent(
            UUID.randomUUID(),
            Instant.now(),
            orderId,
            customerId,
            items.stream().map(OrderItemSnapshot::from).toList(),
            totalAmount,
            shippingAddress
        );
    }

    @Override
    public String aggregateType() { return "Order"; }

    @Override
    public UUID aggregateId() { return orderId; }
}

// OrderCancelledEvent.java
public record OrderCancelledEvent(
    UUID eventId,
    Instant occurredAt,
    UUID orderId,
    String reason,
    Instant cancelledAt
) implements DomainEvent {

    public static OrderCancelledEvent of(UUID orderId, String reason) {
        return new OrderCancelledEvent(
            UUID.randomUUID(), Instant.now(), orderId, reason, Instant.now()
        );
    }

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
}

// OrderShippedEvent.java
public record OrderShippedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID orderId,
    String trackingNumber,
    Instant shippedAt
) implements DomainEvent {

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
}

// ShippingAddressUpdatedEvent.java
public record ShippingAddressUpdatedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID orderId,
    Address newAddress,
    Instant updatedAt
) implements DomainEvent {

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
}

// OrderDeliveredEvent.java
public record OrderDeliveredEvent(
    UUID eventId,
    Instant occurredAt,
    UUID orderId,
    Instant deliveredAt
) implements DomainEvent {

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
}
```

---

## Event Publishing

The command handler publishes events after persisting the aggregate:

```java
public interface DomainEventPublisher {
    void publish(DomainEvent event);
    void publishAll(Collection<DomainEvent> events);
}

// Kafka implementation
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    @Override
    public void publish(DomainEvent event) {
        // Topic name derived from event type: "order-placed", "order-cancelled"
        String topic = toTopicName(event.getClass().getSimpleName());

        // Key = aggregateId ensures ordering within a partition
        kafkaTemplate.send(topic, event.aggregateId().toString(), event);
    }

    private String toTopicName(String eventClassName) {
        // OrderPlacedEvent → order-placed
        return eventClassName
            .replace("Event", "")
            .replaceAll("([A-Z])", "-$1")
            .toLowerCase()
            .substring(1);
    }
}
```

---

## Projectors (Event Handlers on the Read Side)

A **Projector** subscribes to domain events and updates the read store accordingly. Each projector is responsible for maintaining one or more read models.

### OrderSummaryProjector

Maintains the `OrderSummaryView` collection (used for "My Orders" lists):

```java
@Component
public class OrderSummaryProjector {

    private final OrderSummaryViewRepository viewRepository;
    private final CustomerReadRepository customerReadRepository;

    // Kafka listener — processes events from the "order-placed" topic
    @KafkaListener(topics = "order-placed", groupId = "order-summary-projector")
    public void on(OrderPlacedEvent event) {
        // Fetch customer name for denormalisation
        String customerName = customerReadRepository
            .findNameById(event.customerId())
            .orElse("Unknown");

        OrderSummaryView view = new OrderSummaryView(
            event.orderId(),
            event.customerId(),
            customerName,
            OrderStatus.PENDING.name(),
            formatMoney(event.totalAmount()),
            event.items().size(),
            formatInstant(event.occurredAt())
        );

        viewRepository.save(view);
    }

    @KafkaListener(topics = "order-cancelled", groupId = "order-summary-projector")
    public void on(OrderCancelledEvent event) {
        viewRepository.updateStatus(event.orderId(), OrderStatus.CANCELLED.name());
    }

    @KafkaListener(topics = "order-shipped", groupId = "order-summary-projector")
    public void on(OrderShippedEvent event) {
        viewRepository.updateStatus(event.orderId(), OrderStatus.SHIPPED.name());
    }

    @KafkaListener(topics = "order-delivered", groupId = "order-summary-projector")
    public void on(OrderDeliveredEvent event) {
        viewRepository.updateStatus(event.orderId(), OrderStatus.DELIVERED.name());
    }
}
```

### OrderDetailProjector

Maintains the full `OrderDetailView` (used for the order detail page):

```java
@Component
public class OrderDetailProjector {

    private final OrderDetailViewRepository viewRepository;
    private final CustomerReadRepository customerReadRepository;

    @KafkaListener(topics = "order-placed", groupId = "order-detail-projector")
    public void on(OrderPlacedEvent event) {
        CustomerView customer = customerReadRepository.findById(event.customerId())
            .orElseThrow();

        List<OrderItemView> itemViews = event.items().stream()
            .map(item -> new OrderItemView(
                item.productId(),
                item.productName(),
                formatMoney(item.unitPrice()),
                item.quantity(),
                formatMoney(item.subtotal())
            ))
            .toList();

        OrderDetailView view = new OrderDetailView(
            event.orderId(),
            event.customerId(),
            customer.fullName(),
            customer.email(),
            OrderStatus.PENDING.name(),
            itemViews,
            toAddressView(event.shippingAddress()),
            formatMoney(event.totalAmount()),
            formatInstant(event.occurredAt()),
            null    // trackingNumber — not yet shipped
        );

        viewRepository.save(view);
    }

    @KafkaListener(topics = "shipping-address-updated", groupId = "order-detail-projector")
    public void on(ShippingAddressUpdatedEvent event) {
        viewRepository.updateShippingAddress(
            event.orderId(),
            toAddressView(event.newAddress())
        );
    }

    @KafkaListener(topics = "order-shipped", groupId = "order-detail-projector")
    public void on(OrderShippedEvent event) {
        viewRepository.updateStatusAndTracking(
            event.orderId(),
            OrderStatus.SHIPPED.name(),
            event.trackingNumber()
        );
    }
}
```

---

## Event Topics Reference

| Event | Kafka Topic | Produced By | Consumed By |
|-------|-------------|-------------|-------------|
| `OrderPlacedEvent` | `order-placed` | `PlaceOrderCommandHandler` | `OrderSummaryProjector`, `OrderDetailProjector`, `InventoryService`, `NotificationService` |
| `OrderCancelledEvent` | `order-cancelled` | `CancelOrderCommandHandler` | `OrderSummaryProjector`, `OrderDetailProjector`, `InventoryService` (release stock) |
| `OrderShippedEvent` | `order-shipped` | `MarkOrderShippedCommandHandler` | `OrderSummaryProjector`, `OrderDetailProjector`, `NotificationService` |
| `OrderDeliveredEvent` | `order-delivered` | `MarkOrderDeliveredCommandHandler` | `OrderSummaryProjector`, `OrderDetailProjector`, `LoyaltyService` (award points) |
| `ShippingAddressUpdatedEvent` | `shipping-address-updated` | `UpdateShippingAddressCommandHandler` | `OrderDetailProjector` |

---

## Projection Rebuilding

One of the most powerful CQRS/ES capabilities: if a read model is corrupted, slow, or needs a new shape, **rebuild it from scratch** by replaying all events:

```java
@Component
public class ProjectionRebuilder {

    private final EventStore eventStore;
    private final OrderSummaryProjector summaryProjector;
    private final OrderDetailProjector detailProjector;
    private final OrderSummaryViewRepository summaryViewRepository;

    public void rebuildOrderSummaries() {
        // 1. Drop existing read model
        summaryViewRepository.deleteAll();

        // 2. Replay all events in order
        eventStore.streamAll(OrderPlacedEvent.class)
            .forEach(summaryProjector::on);

        eventStore.streamAll(OrderCancelledEvent.class)
            .forEach(summaryProjector::on);

        // ... replay all other event types
    }
}
```

---

## Idempotency

Projectors must be **idempotent** — processing the same event twice should produce the same result. Use the `eventId` to deduplicate:

```java
@KafkaListener(topics = "order-placed")
public void on(OrderPlacedEvent event) {
    // Check if already processed
    if (viewRepository.existsByEventId(event.eventId())) {
        log.warn("Duplicate event received: {}", event.eventId());
        return;
    }

    // Process and store with eventId for deduplication
    OrderSummaryView view = buildView(event);
    view.setLastProcessedEventId(event.eventId());
    viewRepository.save(view);
}
```

---

## Summary

```
Aggregate emits Domain Events
         │
         ▼
  [Write Store] ←── saved
         │
         ▼ publish
   [Kafka Topic]
         │
    ┌────┴────┬──────────────┐
    ▼         ▼              ▼
Projector  Projector    Other Services
(summary)  (detail)    (inventory, notifications)
    │         │
    ▼         ▼
[Read Store updated]
```

> Next: [06-java-implementation.md](./06-java-implementation.md) — Java 21 implementation guide
