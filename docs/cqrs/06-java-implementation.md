# Java 21 Implementation Guide

## Why Java 21?

Java 21 is the current **Long-Term Support (LTS)** release and introduces several language features that make CQRS implementations cleaner and more expressive:

| Feature | Java Version | CQRS Use Case |
|---------|-------------|---------------|
| Records | Java 16+ | Immutable Commands, Queries, and Domain Events |
| Sealed Classes | Java 17+ | Domain exception hierarchies, sum types |
| Pattern Matching for `switch` | Java 21 (finalised) | Event dispatching, status handling |
| Virtual Threads (Project Loom) | Java 21 | High-concurrency query handlers |
| Sequenced Collections | Java 21 | Ordered event lists |
| Text Blocks | Java 15+ | SQL queries in repositories |

---

## Project Setup

### build.gradle (Kotlin DSL)

```kotlin
plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Caching
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Utilities
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:postgresql")
}
```

---

## Package Structure

```
com.example.ecommerce
├── command
│   ├── api
│   │   ├── OrderCommandController.java
│   │   └── dto
│   │       ├── PlaceOrderRequest.java
│   │       └── CancelOrderRequest.java
│   ├── application
│   │   ├── PlaceOrderCommandHandler.java
│   │   ├── CancelOrderCommandHandler.java
│   │   ├── UpdateShippingAddressCommandHandler.java
│   │   └── MarkOrderShippedCommandHandler.java
│   ├── domain
│   │   ├── model
│   │   │   ├── Order.java                    ← Aggregate root
│   │   │   ├── OrderItem.java                ← Entity
│   │   │   ├── OrderStatus.java              ← Enum
│   │   │   ├── Money.java                    ← Value object (record)
│   │   │   └── Address.java                  ← Value object (record)
│   │   ├── event
│   │   │   ├── DomainEvent.java              ← Sealed interface
│   │   │   ├── OrderPlacedEvent.java
│   │   │   ├── OrderCancelledEvent.java
│   │   │   └── OrderShippedEvent.java
│   │   ├── exception
│   │   │   ├── OrderException.java           ← Sealed class
│   │   │   ├── InvalidOrderStateException.java
│   │   │   └── OrderNotFoundException.java
│   │   └── repository
│   │       └── OrderRepository.java          ← Write-side repository
│   └── infrastructure
│       ├── persistence
│       │   └── JpaOrderRepository.java
│       └── messaging
│           └── KafkaDomainEventPublisher.java
│
├── query
│   ├── api
│   │   └── OrderQueryController.java
│   ├── application
│   │   ├── GetOrderByIdQueryHandler.java
│   │   ├── GetOrdersByCustomerQueryHandler.java
│   │   └── GetOrderStatusQueryHandler.java
│   ├── domain
│   │   ├── view
│   │   │   ├── OrderDetailView.java          ← Record DTO
│   │   │   ├── OrderSummaryView.java         ← Record DTO
│   │   │   └── OrderStatusView.java          ← Record DTO
│   │   └── repository
│   │       └── OrderReadRepository.java      ← Read-only interface
│   └── infrastructure
│       ├── persistence
│       │   └── MongoOrderReadRepository.java
│       └── projection
│           ├── OrderSummaryProjector.java
│           └── OrderDetailProjector.java
│
└── shared
    ├── DomainEventPublisher.java
    └── config
        ├── KafkaConfig.java
        └── CacheConfig.java
```

---

## Key Java 21 Features in Action

### 1. Records for Commands and Events

```java
// Immutable, value-based — no boilerplate
public record PlaceOrderCommand(
    UUID customerId,
    List<OrderItemRequest> items,
    Address shippingAddress
) {
    // Compact constructor for validation
    public PlaceOrderCommand {
        Objects.requireNonNull(customerId);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        items = List.copyOf(items); // Defensive copy — ensures immutability
    }
}
```

### 2. Sealed Classes for Domain Exceptions

```java
// Exhaustive exception hierarchy — compiler enforces completeness
public sealed class OrderException extends RuntimeException
    permits OrderNotFoundException, InvalidOrderStateException, InvalidOrderException {

    protected OrderException(String message) { super(message); }
    protected OrderException(String message, Throwable cause) { super(message, cause); }
}

public final class OrderNotFoundException extends OrderException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}

public final class InvalidOrderStateException extends OrderException {
    public InvalidOrderStateException(String message) { super(message); }
}
```

### 3. Pattern Matching for Switch — Event Dispatching

```java
// Java 21 switch pattern matching — exhaustive and type-safe
public void dispatch(DomainEvent event) {
    switch (event) {
        case OrderPlacedEvent e      -> handleOrderPlaced(e);
        case OrderCancelledEvent e   -> handleOrderCancelled(e);
        case OrderShippedEvent e     -> handleOrderShipped(e);
        case OrderDeliveredEvent e   -> handleOrderDelivered(e);
        case ShippingAddressUpdatedEvent e -> handleAddressUpdated(e);
        // Sealed interface — compiler warns if a case is missing
    }
}
```

### 4. Sealed Interface for Domain Events

```java
// Sealed interface — only these implementations are permitted
public sealed interface DomainEvent
    permits OrderPlacedEvent, OrderCancelledEvent, OrderShippedEvent,
            OrderDeliveredEvent, ShippingAddressUpdatedEvent {

    UUID eventId();
    Instant occurredAt();
    String aggregateType();
    UUID aggregateId();
}
```

### 5. Virtual Threads for Query Handlers

```java
// application.properties — enable virtual threads (Java 21)
// spring.threads.virtual.enabled=true

// All Spring MVC threads become virtual threads automatically.
// Query handlers can block on I/O without wasting OS threads.
// This dramatically improves throughput for read-heavy workloads.

@Component
public class GetOrdersByCustomerQueryHandler {

    public Page<OrderSummaryView> handle(GetOrdersByCustomerQuery query) {
        // This blocking database call runs on a virtual thread —
        // extremely cheap, no thread pool exhaustion
        return readRepository.findSummariesByCustomerId(
            query.customerId(),
            PageRequest.of(query.page(), query.pageSize())
        );
    }
}
```

### 6. Text Blocks for SQL Queries

```java
@Repository
public class JdbcOrderReadRepository implements OrderReadRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<OrderDetailView> findOrderDetailById(UUID orderId) {
        String sql = """
            SELECT
                o.order_id,
                o.status,
                o.total_amount,
                o.placed_at,
                c.full_name AS customer_name,
                c.email     AS customer_email,
                s.tracking_number
            FROM orders o
            JOIN customers c ON c.customer_id = o.customer_id
            LEFT JOIN shipments s ON s.order_id = o.order_id
            WHERE o.order_id = :orderId
            """;

        // ... execute and map
    }
}
```

---

## Configuration

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce_write
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  data:
    mongodb:
      uri: mongodb://localhost:27017/ecommerce_read

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: ecommerce-projectors
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest

  threads:
    virtual:
      enabled: true   # Java 21 Virtual Threads

  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=60s
```

---

## Testing Strategy

### Unit Test — Aggregate

```java
class OrderTest {

    @Test
    void shouldPlaceOrderSuccessfully() {
        List<OrderItem> items = List.of(
            OrderItem.of(UUID.randomUUID(), "Laptop", Money.of("999.99", "GBP"), 1)
        );
        Address address = new Address("1 Test St", "London", "England", "SW1A 1AA", "GB");

        Order order = Order.place(UUID.randomUUID(), items, address);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getDomainEvents()).hasSize(1);
        assertThat(order.getDomainEvents().get(0)).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void shouldRejectCancellationOfShippedOrder() {
        Order order = buildShippedOrder();

        assertThatThrownBy(() -> order.cancel("Customer changed mind"))
            .isInstanceOf(InvalidOrderStateException.class)
            .hasMessageContaining("Cannot cancel order");
    }

    @Test
    void shouldNotAllowOrderWithNoItems() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), List.of(), someAddress()))
            .isInstanceOf(InvalidOrderException.class)
            .hasMessage("Order must contain at least one item");
    }
}
```

### Unit Test — Command Handler

```java
class PlaceOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private PlaceOrderCommandHandler handler;

    @Test
    void shouldCreateOrderAndPublishEvent() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(productRepository.findById(productId))
            .thenReturn(Optional.of(aProduct(productId)));

        PlaceOrderCommand command = new PlaceOrderCommand(
            customerId,
            List.of(new OrderItemRequest(productId, 2)),
            anAddress()
        );

        UUID orderId = handler.handle(command);

        assertThat(orderId).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(any(OrderPlacedEvent.class));
    }
}
```

### Integration Test — With Testcontainers

```java
@SpringBootTest
@Testcontainers
class OrderCommandIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private PlaceOrderCommandHandler handler;
    @Autowired private OrderRepository orderRepository;

    @Test
    void shouldPersistOrderToDatabase() {
        PlaceOrderCommand command = aValidPlaceOrderCommand();

        UUID orderId = handler.handle(command);

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

---

## Error Handling

```java
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleConflict(InvalidOrderStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Uses Spring 6 RFC 7807 Problem Details
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail("Validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList());
        return problem;
    }
}
```

> Next: [07-project-structure.md](./07-project-structure.md) — Full module breakdown
