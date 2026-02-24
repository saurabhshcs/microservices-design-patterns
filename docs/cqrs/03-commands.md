# Commands — The Write Side

## What Is a Command?

A **Command** expresses an **intent to change system state**. Commands are:

- Named in the **imperative form** — `PlaceOrder`, not `OrderPlaced`
- **Targeted** — sent to one specific aggregate
- **Validated** before being handled
- Either **accepted** (and produce side effects) or **rejected** (with an error)
- **Never return data** — a command handler returns void or a success/failure indicator

Think of a command as a request to do something. The system may refuse it.

---

## eCommerce Commands

### Order Commands

| Command | Handler | Description |
|---------|---------|-------------|
| `PlaceOrderCommand` | `PlaceOrderCommandHandler` | Creates a new order from a shopping cart |
| `CancelOrderCommand` | `CancelOrderCommandHandler` | Cancels an order (only valid before shipping) |
| `UpdateShippingAddressCommand` | `UpdateShippingAddressCommandHandler` | Changes delivery address (only valid before shipping) |
| `MarkOrderShippedCommand` | `MarkOrderShippedCommandHandler` | Records that an order has been handed to a carrier |
| `MarkOrderDeliveredCommand` | `MarkOrderDeliveredCommandHandler` | Records successful delivery |
| `ConfirmOrderCommand` | `ConfirmOrderCommandHandler` | Confirms order after payment success |

---

## Command Definitions (Java 21)

Using Java 21 **records** for immutable, concise command definitions:

```java
// PlaceOrderCommand.java
public record PlaceOrderCommand(
    UUID customerId,
    List<OrderItemRequest> items,
    Address shippingAddress
) {
    public PlaceOrderCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(shippingAddress, "shippingAddress must not be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
    }
}

// OrderItemRequest.java
public record OrderItemRequest(
    UUID productId,
    int quantity
) {
    public OrderItemRequest {
        Objects.requireNonNull(productId, "productId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}

// CancelOrderCommand.java
public record CancelOrderCommand(
    UUID orderId,
    UUID customerId,    // for authorisation check
    String reason
) {}

// UpdateShippingAddressCommand.java
public record UpdateShippingAddressCommand(
    UUID orderId,
    UUID customerId,
    Address newAddress
) {}

// MarkOrderShippedCommand.java
public record MarkOrderShippedCommand(
    UUID orderId,
    String trackingNumber,
    Instant shippedAt
) {}
```

---

## Command Handlers

A **Command Handler** is responsible for:
1. Loading the aggregate from the repository
2. Calling the appropriate domain method
3. Persisting the updated aggregate
4. Publishing the resulting domain events

### PlaceOrderCommandHandler

```java
@Component
public class PlaceOrderCommandHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    public PlaceOrderCommandHandler(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UUID handle(PlaceOrderCommand command) {
        // 1. Resolve product details (price snapshot)
        List<OrderItem> items = resolveOrderItems(command.items());

        // 2. Create the aggregate — domain logic inside
        Order order = Order.place(command.customerId(), items, command.shippingAddress());

        // 3. Persist
        orderRepository.save(order);

        // 4. Publish domain events
        order.getDomainEvents().forEach(eventPublisher::publish);
        order.clearDomainEvents();

        return order.getOrderId();
    }

    private List<OrderItem> resolveOrderItems(List<OrderItemRequest> requests) {
        return requests.stream()
            .map(req -> {
                Product product = productRepository.findById(req.productId())
                    .orElseThrow(() -> new ProductNotFoundException(req.productId()));
                return OrderItem.of(product, req.quantity());
            })
            .toList();
    }
}
```

### CancelOrderCommandHandler

```java
@Component
public class CancelOrderCommandHandler {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void handle(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
            .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // Domain logic enforces the invariant (cannot cancel shipped orders)
        order.cancel(command.reason());

        orderRepository.save(order);
        order.getDomainEvents().forEach(eventPublisher::publish);
        order.clearDomainEvents();
    }
}
```

---

## The Order Aggregate — Domain Logic

All business rules live inside the aggregate. Command handlers are thin orchestrators.

```java
public class Order {

    private UUID orderId;
    private UUID customerId;
    private OrderStatus status;
    private List<OrderItem> items;
    private Address shippingAddress;
    private Money totalAmount;
    private Instant placedAt;
    private Instant updatedAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Factory method — validates and creates
    public static Order place(UUID customerId, List<OrderItem> items, Address shippingAddress) {
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }

        Order order = new Order();
        order.orderId = UUID.randomUUID();
        order.customerId = customerId;
        order.items = List.copyOf(items);
        order.shippingAddress = shippingAddress;
        order.totalAmount = calculateTotal(items);
        order.status = OrderStatus.PENDING;
        order.placedAt = Instant.now();
        order.updatedAt = order.placedAt;

        // Record the event
        order.domainEvents.add(new OrderPlacedEvent(
            order.orderId, customerId, items, order.totalAmount, shippingAddress, order.placedAt
        ));

        return order;
    }

    // Business method — enforces invariant
    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException(
                "Cannot cancel order " + orderId + " — already " + status
            );
        }
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order " + orderId + " is already cancelled");
        }

        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();

        domainEvents.add(new OrderCancelledEvent(orderId, reason, updatedAt));
    }

    // Business method
    public void updateShippingAddress(Address newAddress) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException(
                "Cannot update shipping address — order already " + status
            );
        }

        this.shippingAddress = newAddress;
        this.updatedAt = Instant.now();

        domainEvents.add(new ShippingAddressUpdatedEvent(orderId, newAddress, updatedAt));
    }

    // Business method
    public void markShipped(String trackingNumber) {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                "Can only ship a CONFIRMED order, current status: " + status
            );
        }

        this.status = OrderStatus.SHIPPED;
        this.updatedAt = Instant.now();

        domainEvents.add(new OrderShippedEvent(orderId, trackingNumber, updatedAt));
    }

    private static Money calculateTotal(List<OrderItem> items) {
        return items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.zero(Currency.getInstance("GBP")), Money::add);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getters omitted for brevity
}
```

---

## Command Validation

Commands can be validated at multiple layers:

### Layer 1 — Input Validation (API Layer)

```java
// Spring Boot controller with Bean Validation
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {

    private final PlaceOrderCommandHandler placeOrderHandler;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderCommand command = OrderCommandMapper.toCommand(request);
        UUID orderId = placeOrderHandler.handle(command);
        return Map.of("orderId", orderId);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody CancelOrderRequest request) {
        CancelOrderCommand command = new CancelOrderCommand(
            orderId, request.customerId(), request.reason()
        );
        cancelOrderHandler.handle(command);
    }
}
```

### Layer 2 — Domain Validation (Aggregate)

Business invariants are enforced inside the aggregate via exceptions:

```java
// Domain exceptions — use sealed classes (Java 17+)
public sealed class OrderException extends RuntimeException
    permits InvalidOrderException, InvalidOrderStateException, OrderNotFoundException {

    protected OrderException(String message) {
        super(message);
    }
}

public final class InvalidOrderStateException extends OrderException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
```

---

## Command Bus Pattern (Optional)

For decoupling controllers from handlers, introduce a **Command Bus**:

```java
public interface CommandBus {
    <R> R dispatch(Command<R> command);
}

// Usage in controller:
UUID orderId = commandBus.dispatch(new PlaceOrderCommand(customerId, items, address));
```

This allows middleware (logging, validation, authorisation) to be added without changing handlers.

---

## Summary

```
Client Request
     │
     ▼
[API Layer]          ← Bean Validation (@Valid)
     │
     ▼ Command object (immutable record)
[Command Handler]    ← Thin orchestrator
     │
     ├── Load Aggregate from Repository
     ├── Call domain method (business logic lives here)
     ├── Persist updated Aggregate
     └── Publish Domain Events
              │
              ▼
       [Event Bus] ──► Projectors update Read Models
```

> Next: [04-queries.md](./04-queries.md) — The Query (Read) side
