# Queries — The Read Side

## What Is a Query?

A **Query** requests data without changing any state. Queries are:

- Named as **questions** or noun phrases — `GetOrderById`, `GetOrdersByCustomer`
- **Side-effect free** — they never change anything
- Optimised for **read performance** using denormalised projections
- Served from a **read store** that may be separate from the write store

> If a command says "do this", a query says "show me this".

---

## Read Model vs Write Model

The query side does **not** use the domain aggregates. It reads from **projections** — pre-built, denormalised views stored in a read-optimised database.

```
Write Store (PostgreSQL)                    Read Store (MongoDB / Redis)
┌─────────────────────────┐                ┌──────────────────────────────┐
│ orders (normalised)     │                │ order_summary_view           │
│ ├── order_id            │                │ ├── orderId                  │
│ ├── customer_id (FK)    │                │ ├── customerName  ← joined   │
│ ├── status              │                │ ├── status                   │
│ └── placed_at           │                │ ├── totalAmount (formatted)  │
│                         │  Projector     │ ├── itemCount    ← counted   │
│ order_items (normalised)│ ──────────────►│ └── placedAt (formatted)     │
│ ├── order_item_id       │                │                              │
│ ├── order_id (FK)       │                │ order_detail_view            │
│ ├── product_id (FK)     │                │ ├── orderId                  │
│ └── quantity            │                │ ├── items (embedded array)   │
│                         │                │ ├── shippingAddress          │
│ customers               │                │ └── trackingNumber           │
│ ├── customer_id         │                │                              │
│ └── full_name           │                └──────────────────────────────┘
└─────────────────────────┘
```

---

## eCommerce Queries

| Query | Handler | Read Model Used | Use Case |
|-------|---------|-----------------|----------|
| `GetOrderByIdQuery` | `GetOrderByIdQueryHandler` | `OrderDetailView` | Order detail page |
| `GetOrdersByCustomerQuery` | `GetOrdersByCustomerQueryHandler` | `OrderSummaryView` | "My Orders" list |
| `GetOrderStatusQuery` | `GetOrderStatusQueryHandler` | `OrderStatusView` | Status polling |
| `GetRecentOrdersQuery` | `GetRecentOrdersQueryHandler` | `OrderSummaryView` | Admin dashboard |
| `GetOrderSummaryQuery` | `GetOrderSummaryQueryHandler` | `OrderSummaryView` | Order confirmation page |
| `GetProductCatalogueQuery` | `GetProductCatalogueQueryHandler` | `ProductView` | Product listing |
| `SearchProductsQuery` | `SearchProductsQueryHandler` | `ProductSearchView` | Search results |

---

## Query Definitions (Java 21)

```java
// GetOrderByIdQuery.java
public record GetOrderByIdQuery(UUID orderId) {}

// GetOrdersByCustomerQuery.java
public record GetOrdersByCustomerQuery(
    UUID customerId,
    int page,
    int pageSize
) {
    public GetOrdersByCustomerQuery {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
    }
}

// GetOrderStatusQuery.java
public record GetOrderStatusQuery(UUID orderId) {}

// SearchProductsQuery.java
public record SearchProductsQuery(
    String searchTerm,
    String category,        // nullable
    BigDecimal maxPrice,    // nullable
    int page,
    int pageSize
) {}
```

---

## Read Model DTOs (Java 21)

```java
// OrderDetailView.java
public record OrderDetailView(
    UUID orderId,
    UUID customerId,
    String customerName,
    String customerEmail,
    String status,
    List<OrderItemView> items,
    AddressView shippingAddress,
    String totalAmount,
    String placedAt,
    String trackingNumber       // null if not shipped yet
) {}

// OrderItemView.java
public record OrderItemView(
    UUID productId,
    String productName,
    String unitPrice,
    int quantity,
    String subtotal
) {}

// OrderSummaryView.java
public record OrderSummaryView(
    UUID orderId,
    String status,
    String totalAmount,
    int itemCount,
    String placedAt
) {}

// OrderStatusView.java
public record OrderStatusView(
    UUID orderId,
    String status,
    String trackingNumber,
    String estimatedDelivery
) {}

// AddressView.java
public record AddressView(
    String street,
    String city,
    String state,
    String postalCode,
    String country
) {}
```

---

## Query Handlers

Query handlers are simple — they load from the read store and return a DTO. No domain logic.

### GetOrderByIdQueryHandler

```java
@Component
public class GetOrderByIdQueryHandler {

    private final OrderReadRepository readRepository;

    public GetOrderByIdQueryHandler(OrderReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public OrderDetailView handle(GetOrderByIdQuery query) {
        return readRepository.findOrderDetailById(query.orderId())
            .orElseThrow(() -> new OrderNotFoundException(query.orderId()));
    }
}
```

### GetOrdersByCustomerQueryHandler

```java
@Component
public class GetOrdersByCustomerQueryHandler {

    private final OrderReadRepository readRepository;

    public Page<OrderSummaryView> handle(GetOrdersByCustomerQuery query) {
        Pageable pageable = PageRequest.of(
            query.page(),
            query.pageSize(),
            Sort.by(Sort.Direction.DESC, "placedAt")
        );
        return readRepository.findSummariesByCustomerId(query.customerId(), pageable);
    }
}
```

### SearchProductsQueryHandler

```java
@Component
public class SearchProductsQueryHandler {

    private final ProductReadRepository productReadRepository;

    public Page<ProductView> handle(SearchProductsQuery query) {
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
            .searchTerm(query.searchTerm())
            .category(query.category())
            .maxPrice(query.maxPrice())
            .build();

        Pageable pageable = PageRequest.of(query.page(), query.pageSize());

        return productReadRepository.search(criteria, pageable);
    }
}
```

---

## Read Repository Interface

The read repository has **no concept of saving**. It is read-only:

```java
public interface OrderReadRepository {

    Optional<OrderDetailView> findOrderDetailById(UUID orderId);

    Page<OrderSummaryView> findSummariesByCustomerId(UUID customerId, Pageable pageable);

    Optional<OrderStatusView> findStatusById(UUID orderId);

    List<OrderSummaryView> findRecentOrders(int limit);
}
```

---

## API Layer (Query Side)

```java
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private final GetOrderByIdQueryHandler getOrderByIdHandler;
    private final GetOrdersByCustomerQueryHandler getOrdersByCustomerHandler;

    @GetMapping("/{orderId}")
    public OrderDetailView getOrder(@PathVariable UUID orderId) {
        return getOrderByIdHandler.handle(new GetOrderByIdQuery(orderId));
    }

    @GetMapping
    public Page<OrderSummaryView> getOrdersByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getOrdersByCustomerHandler.handle(
            new GetOrdersByCustomerQuery(customerId, page, size)
        );
    }

    @GetMapping("/{orderId}/status")
    public OrderStatusView getOrderStatus(@PathVariable UUID orderId) {
        return getOrderStatusHandler.handle(new GetOrderStatusQuery(orderId));
    }
}
```

---

## Performance Optimisation Strategies

### Caching

For frequently accessed, slowly changing read models (e.g., product catalogue):

```java
@Component
public class CachingProductQueryHandler {

    @Cacheable(value = "products", key = "#query.productId()")
    public ProductView handle(GetProductByIdQuery query) {
        return productReadRepository.findById(query.productId())
            .orElseThrow(() -> new ProductNotFoundException(query.productId()));
    }

    @CacheEvict(value = "products", key = "#event.productId()")
    @EventListener
    public void onProductUpdated(ProductUpdatedEvent event) {
        // Cache is invalidated automatically when product is updated
    }
}
```

### Read Store Selection by Use Case

| Query Type | Recommended Store | Reason |
|-----------|-------------------|--------|
| Single entity lookup | Redis | Sub-millisecond reads via key lookup |
| Full-text search | Elasticsearch | Tokenised search, ranking |
| Order history (paginated) | PostgreSQL | Relational, indexed queries |
| Analytics / aggregations | ClickHouse | Columnar, fast aggregations |
| Recommendation data | MongoDB | Flexible document model |

---

## Consistency Lag (The Trade-off)

Because the read model is **updated asynchronously** (via events), there is a brief window where:

- A command succeeds (order placed in write store)
- The read model has not yet been updated (order not visible in "My Orders")

To handle this gracefully:

```java
// After placing an order, redirect to a "processing" page
// that polls the status endpoint until the order appears
@GetMapping("/{orderId}/status")
public OrderStatusView getOrderStatus(@PathVariable UUID orderId) {
    return getOrderStatusHandler.handle(new GetOrderStatusQuery(orderId));
    // Returns: { "status": "PENDING" } — available immediately from write DB
    // Or use a separate fast-path projection updated synchronously
}
```

> In practice, event propagation is typically under 100ms in a well-tuned system, making the lag imperceptible to users.

---

## Summary

```
Client Request (GET)
      │
      ▼
[API Layer — Query Controller]
      │
      ▼ Query object (immutable record)
[Query Handler]    ← No domain logic, no writes
      │
      ▼
[Read Repository]  ← Read-only, optimised for this query
      │
      ▼
[Read Store]       ← MongoDB / Redis / Elasticsearch / PostgreSQL View
      │
      ▼
[DTO returned]     ← OrderDetailView, OrderSummaryView, etc.
```

> Next: [05-events.md](./05-events.md) — Domain Events and Projectors
