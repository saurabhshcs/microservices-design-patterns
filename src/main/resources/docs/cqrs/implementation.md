# CQRS -- Implementation

> **Stack:** Spring Boot 4.0.0, Java 17, Spring Data JPA, PostgreSQL, Lombok
> **Pattern variant:** Manual CQRS with Spring `ApplicationEventPublisher` (no Axon Framework)

---

## Table of Contents

1. [Domain Events](#1-domain-events)
2. [Command Objects](#2-command-objects)
3. [Query Objects](#3-query-objects)
4. [Write-Side Domain Model](#4-write-side-domain-model)
5. [Write-Side Repository](#5-write-side-repository)
6. [Command Handlers](#6-command-handlers)
7. [Read-Side Model (Projection)](#7-read-side-model-projection)
8. [Read-Side Repository](#8-read-side-repository)
9. [Read Model Projector](#9-read-model-projector)
10. [Query Handlers](#10-query-handlers)
11. [REST Controllers](#11-rest-controllers)
12. [Configuration](#12-configuration)
13. [Application Properties](#13-application-properties)

---

## 1. Domain Events

Events represent facts that happened in the system. They are immutable and carry all data needed by projectors.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published after an order is successfully placed.
 * Carries a complete snapshot so projectors never need to query the write store.
 */
public record OrderPlacedEvent(
        UUID orderId,
        UUID customerId,
        List<OrderLineItem> items,
        BigDecimal totalAmount,
        String shippingAddress,
        Instant placedAt
) {
    /**
     * Embedded line-item detail for the event payload.
     */
    public record OrderLineItem(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an order is cancelled by an admin or the customer.
 */
public record OrderCancelledEvent(
        UUID orderId,
        UUID cancelledBy,
        String reason,
        Instant cancelledAt
) {}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an order transitions to SHIPPED status.
 */
public record OrderShippedEvent(
        UUID orderId,
        String trackingNumber,
        Instant shippedAt
) {}
```

---

## 2. Command Objects

Commands are value objects that express an **intent to change state**. They carry only the data needed for validation and persistence.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.command;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Command to place a new order.
 *
 * @param customerId      the customer placing the order
 * @param items           one or more line items
 * @param shippingAddress delivery address
 */
public record PlaceOrderCommand(
        @NotNull UUID customerId,
        @NotEmpty List<LineItemDto> items,
        @NotNull String shippingAddress
) {
    public record LineItemDto(
            @NotNull UUID productId,
            @NotNull String productName,
            int quantity,
            @NotNull BigDecimal unitPrice
    ) {}
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to cancel an existing order.
 *
 * @param orderId     the order to cancel
 * @param cancelledBy the user requesting cancellation
 * @param reason      human-readable cancellation reason
 */
public record CancelOrderCommand(
        @NotNull UUID orderId,
        @NotNull UUID cancelledBy,
        String reason
) {}
```

---

## 3. Query Objects

Queries are value objects that describe what data the caller needs. They keep the controller layer thin.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.query;

import java.util.UUID;

/**
 * Query for paginated order history of a specific customer.
 *
 * @param customerId the customer whose orders to retrieve
 * @param page       zero-based page index
 * @param size       page size
 */
public record GetOrdersByCustomerQuery(
        UUID customerId,
        int page,
        int size
) {}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.query;

import java.util.UUID;

/**
 * Query for a single order's full detail.
 *
 * @param orderId the order to look up
 */
public record GetOrderDetailQuery(UUID orderId) {}
```

---

## 4. Write-Side Domain Model

The write model is **normalised** and enforces all invariants via JPA entity logic.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Order aggregate root on the write side.
 * <p>
 * This entity is normalised: line items live in a separate table with a
 * foreign-key relationship. All invariant checks (e.g., "only cancel if
 * not yet shipped") belong here.
 * </p>
 */
@Entity
@Table(name = "orders", schema = "write_store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "lineItems")
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    private Instant shippedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    /**
     * Factory method -- the only way to create a new Order.
     */
    public static Order place(UUID customerId,
                              String shippingAddress,
                              List<OrderLineItem> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.shippingAddress = shippingAddress;
        order.status = OrderStatus.PLACED;
        order.createdAt = Instant.now();
        order.totalAmount = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        items.forEach(item -> item.setOrder(order));
        order.lineItems.addAll(items);
        return order;
    }

    /**
     * Cancel the order. Throws if the order has already shipped.
     */
    public void cancel(String reason) {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot cancel order %s -- current status is %s".formatted(id, status));
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    /**
     * Mark the order as shipped.
     */
    public void ship(String trackingNumber) {
        if (this.status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Cannot ship order %s -- current status is %s".formatted(id, status));
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain;

/**
 * Possible states of an order in the write model.
 */
public enum OrderStatus {
    PLACED,
    CANCELLED,
    SHIPPED,
    DELIVERED
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item belonging to an {@link Order}.
 */
@Entity
@Table(name = "order_line_items", schema = "write_store")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLineItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    public OrderLineItem(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }
}
```

---

## 5. Write-Side Repository

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.write;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the write-side Order aggregate.
 * Intentionally has NO query methods beyond findById -- reads go through
 * the read-side repository.
 */
@Repository
public interface OrderWriteRepository extends JpaRepository<Order, UUID> {
}
```

---

## 6. Command Handlers

Each handler encapsulates the business logic for a single command. Handlers publish domain events after the write transaction commits.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.command.PlaceOrderCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain.OrderLineItem;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.event.OrderPlacedEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.write.OrderWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles the {@link PlaceOrderCommand} by persisting the Order aggregate
 * and publishing an {@link OrderPlacedEvent}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceOrderCommandHandler {

    private final OrderWriteRepository orderWriteRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Execute the command inside a transaction. The event is published
     * in-process; the projector listens with AFTER_COMMIT phase so it
     * only fires if the transaction succeeds.
     *
     * @param command the place-order command
     * @return the generated order ID
     */
    @Transactional
    public UUID handle(PlaceOrderCommand command) {
        log.info("Handling PlaceOrderCommand for customer {}", command.customerId());

        // Map DTOs to domain entities
        var lineItems = command.items().stream()
                .map(dto -> new OrderLineItem(
                        dto.productId(),
                        dto.productName(),
                        dto.quantity(),
                        dto.unitPrice()))
                .toList();

        // Create and persist the aggregate
        Order order = Order.place(command.customerId(), command.shippingAddress(), lineItems);
        orderWriteRepository.save(order);

        // Build and publish the event
        var eventItems = command.items().stream()
                .map(dto -> new OrderPlacedEvent.OrderLineItem(
                        dto.productId(),
                        dto.productName(),
                        dto.quantity(),
                        dto.unitPrice()))
                .toList();

        eventPublisher.publishEvent(new OrderPlacedEvent(
                order.getId(),
                order.getCustomerId(),
                eventItems,
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getCreatedAt()
        ));

        log.info("Order {} placed successfully", order.getId());
        return order.getId();
    }
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.command.CancelOrderCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.domain.Order;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.event.OrderCancelledEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.write.OrderWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles the {@link CancelOrderCommand} by loading the Order aggregate,
 * applying the cancellation, and publishing an {@link OrderCancelledEvent}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancelOrderCommandHandler {

    private final OrderWriteRepository orderWriteRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param command the cancel-order command
     * @throws IllegalStateException    if the order cannot be cancelled
     * @throws jakarta.persistence.EntityNotFoundException if the order does not exist
     */
    @Transactional
    public void handle(CancelOrderCommand command) {
        log.info("Handling CancelOrderCommand for order {}", command.orderId());

        Order order = orderWriteRepository.findById(command.orderId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Order not found: " + command.orderId()));

        order.cancel(command.reason());
        orderWriteRepository.save(order);

        eventPublisher.publishEvent(new OrderCancelledEvent(
                order.getId(),
                command.cancelledBy(),
                command.reason(),
                Instant.now()
        ));

        log.info("Order {} cancelled", order.getId());
    }
}
```

---

## 7. Read-Side Model (Projection)

The read model is **denormalised** -- a single flat table that can serve the order-history dashboard with no JOINs.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.readmodel;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Denormalised read-side projection of an order.
 * <p>
 * This entity maps to a flat table designed for fast reads. It includes
 * pre-computed fields like {@code itemsSummary} (a comma-separated product
 * list) so the query layer never needs to JOIN.
 * </p>
 */
@Entity
@Table(name = "order_summary_view", schema = "read_store")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryView {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    /** Comma-separated summary, e.g. "Widget x2, Gadget x1" */
    @Column(length = 2000)
    private String itemsSummary;

    private int totalItems;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private Instant placedAt;

    private Instant cancelledAt;

    private Instant shippedAt;

    private String trackingNumber;
}
```

---

## 8. Read-Side Repository

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.read;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.readmodel.OrderSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the read-side OrderSummaryView projection.
 * Contains rich query methods that would be expensive on the write-side schema.
 */
@Repository
public interface OrderSummaryViewRepository extends JpaRepository<OrderSummaryView, UUID> {

    /**
     * Paginated order history for a customer, newest first.
     */
    Page<OrderSummaryView> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    /**
     * Find all orders with a specific status for admin dashboards.
     */
    Page<OrderSummaryView> findByStatusOrderByPlacedAtDesc(String status, Pageable pageable);
}
```

---

## 9. Read Model Projector

The projector listens for domain events and updates the read store. It is **idempotent** -- re-processing the same event produces the same result.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.projector;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.event.OrderCancelledEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.event.OrderPlacedEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.event.OrderShippedEvent;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.readmodel.OrderSummaryView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.read.OrderSummaryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.stream.Collectors;

/**
 * Projects write-side domain events into the denormalised read store.
 * <p>
 * Each handler is annotated with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * so it only executes if the originating command transaction committed successfully.
 * This prevents phantom reads in the read model.
 * </p>
 *
 * <p><strong>Idempotency:</strong> All operations use {@code save()} which performs
 * an UPSERT by primary key. Re-processing the same event is safe.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReadModelProjector {

    private final OrderSummaryViewRepository readRepository;

    /**
     * Project a newly placed order into the read store.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlacedEvent event) {
        log.info("Projecting OrderPlacedEvent for order {}", event.orderId());

        String itemsSummary = event.items().stream()
                .map(i -> "%s x%d".formatted(i.productName(), i.quantity()))
                .collect(Collectors.joining(", "));

        int totalItems = event.items().stream()
                .mapToInt(OrderPlacedEvent.OrderLineItem::quantity)
                .sum();

        OrderSummaryView view = OrderSummaryView.builder()
                .orderId(event.orderId())
                .customerId(event.customerId())
                .itemsSummary(itemsSummary)
                .totalItems(totalItems)
                .totalAmount(event.totalAmount())
                .status("PLACED")
                .shippingAddress(event.shippingAddress())
                .placedAt(event.placedAt())
                .build();

        readRepository.save(view);
        log.debug("Read model updated for order {}", event.orderId());
    }

    /**
     * Update the read model when an order is cancelled.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelledEvent event) {
        log.info("Projecting OrderCancelledEvent for order {}", event.orderId());

        readRepository.findById(event.orderId()).ifPresent(view -> {
            view.setStatus("CANCELLED");
            view.setCancelledAt(event.cancelledAt());
            readRepository.save(view);
        });
    }

    /**
     * Update the read model when an order ships.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderShippedEvent event) {
        log.info("Projecting OrderShippedEvent for order {}", event.orderId());

        readRepository.findById(event.orderId()).ifPresent(view -> {
            view.setStatus("SHIPPED");
            view.setShippedAt(event.shippedAt());
            view.setTrackingNumber(event.trackingNumber());
            readRepository.save(view);
        });
    }
}
```

---

## 10. Query Handlers

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.query.GetOrderDetailQuery;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.query.GetOrdersByCustomerQuery;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.readmodel.OrderSummaryView;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.repository.read.OrderSummaryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles all read-side queries by fetching data from the denormalised read store.
 * <p>
 * Read transactions are marked {@code readOnly = true} so Spring can apply
 * optimisations (no flush, no dirty checking, read replicas in cloud setups).
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryHandler {

    private final OrderSummaryViewRepository readRepository;

    /**
     * Retrieve paginated order history for a customer.
     */
    @Transactional(readOnly = true)
    public Page<OrderSummaryView> handle(GetOrdersByCustomerQuery query) {
        log.debug("Handling GetOrdersByCustomerQuery for customer {}", query.customerId());
        return readRepository.findByCustomerIdOrderByPlacedAtDesc(
                query.customerId(),
                PageRequest.of(query.page(), query.size())
        );
    }

    /**
     * Retrieve a single order detail.
     *
     * @throws jakarta.persistence.EntityNotFoundException if the order is not in the read store
     */
    @Transactional(readOnly = true)
    public OrderSummaryView handle(GetOrderDetailQuery query) {
        log.debug("Handling GetOrderDetailQuery for order {}", query.orderId());
        return readRepository.findById(query.orderId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Order not found in read store: " + query.orderId()));
    }
}
```

---

## 11. REST Controllers

The API layer is intentionally thin -- it delegates entirely to command/query handlers.

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.controller;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.command.CancelOrderCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.command.PlaceOrderCommand;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler.CancelOrderCommandHandler;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler.PlaceOrderCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for write operations (commands).
 * Accepts command payloads and delegates to the appropriate handler.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderCommandController {

    private final PlaceOrderCommandHandler placeOrderHandler;
    private final CancelOrderCommandHandler cancelOrderHandler;

    /**
     * Place a new order.
     *
     * @param command the order details
     * @return 201 Created with the order URI in the Location header
     */
    @PostMapping
    public ResponseEntity<Void> placeOrder(@Valid @RequestBody PlaceOrderCommand command) {
        UUID orderId = placeOrderHandler.handle(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(orderId)
                .toUri();
        return ResponseEntity.created(location).build();
    }

    /**
     * Cancel an existing order.
     *
     * @param orderId the order to cancel
     * @param command cancellation details (cancelledBy, reason)
     * @return 204 No Content on success
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderCommand command) {
        // Override the path variable into the command for consistency
        cancelOrderHandler.handle(new CancelOrderCommand(orderId, command.cancelledBy(), command.reason()));
        return ResponseEntity.noContent().build();
    }
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.controller;

import com.saurabhshcs.adtech.microservices.designpattern.cqrs.handler.OrderQueryHandler;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.query.GetOrderDetailQuery;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.query.GetOrdersByCustomerQuery;
import com.saurabhshcs.adtech.microservices.designpattern.cqrs.readmodel.OrderSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for read operations (queries).
 * Returns data from the denormalised read store -- no JOINs, no write-side access.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderQueryController {

    private final OrderQueryHandler queryHandler;

    /**
     * Get paginated order history for a customer.
     *
     * @param customerId the customer ID
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @return paginated list of order summaries
     */
    @GetMapping
    public ResponseEntity<Page<OrderSummaryView>> getOrdersByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var query = new GetOrdersByCustomerQuery(customerId, page, size);
        return ResponseEntity.ok(queryHandler.handle(query));
    }

    /**
     * Get a single order detail.
     *
     * @param orderId the order ID
     * @return the order summary view
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderSummaryView> getOrderDetail(@PathVariable UUID orderId) {
        var query = new GetOrderDetailQuery(orderId);
        return ResponseEntity.ok(queryHandler.handle(query));
    }
}
```

---

## 12. Configuration

```java
package com.saurabhshcs.adtech.microservices.designpattern.cqrs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * CQRS infrastructure configuration.
 * <p>
 * {@code @EnableAsync} allows event listeners to run asynchronously if needed
 * in future iterations (e.g., when projecting to an external read store like
 * Elasticsearch).
 * </p>
 */
@Configuration
@EnableAsync
public class CqrsConfig {
    // Spring's ApplicationEventPublisher is auto-configured.
    // No additional bean definitions required for in-process CQRS.
}
```

---

## 13. Application Properties

```yaml
# application.yml -- CQRS module configuration

spring:
  application:
    name: cqrs-order-service

  datasource:
    url: jdbc:postgresql://localhost:5432/shopstream
    username: shopstream_app
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000

  jpa:
    hibernate:
      ddl-auto: validate          # Use Flyway/Liquibase for migrations in prod
    properties:
      hibernate:
        default_schema: write_store
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false           # Disable OSIV -- queries go through handlers

  # Read store can use a separate datasource in production.
  # For simplicity, this example uses schemas within the same database.

server:
  port: 8081

logging:
  level:
    com.saurabhshcs.adtech.microservices.designpattern.cqrs: DEBUG
    org.springframework.transaction: DEBUG
```

---

## Example API Payloads

### Place an Order

**Request:**

```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "customerId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "items": [
    {
      "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productName": "Wireless Keyboard",
      "quantity": 2,
      "unitPrice": 49.99
    },
    {
      "productId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "productName": "USB-C Hub",
      "quantity": 1,
      "unitPrice": 29.99
    }
  ],
  "shippingAddress": "123 Main St, Apt 4B, San Francisco, CA 94102"
}
```

**Response:**

```http
HTTP/1.1 201 Created
Location: /api/orders/e3b0c442-98fc-1c14-b39f-f32d40a1b2c3
```

### Query Order History

**Request:**

```http
GET /api/orders?customerId=f47ac10b-58cc-4372-a567-0e02b2c3d479&page=0&size=10 HTTP/1.1
```

**Response:**

```json
{
  "content": [
    {
      "orderId": "e3b0c442-98fc-1c14-b39f-f32d40a1b2c3",
      "customerId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "itemsSummary": "Wireless Keyboard x2, USB-C Hub x1",
      "totalItems": 3,
      "totalAmount": 129.97,
      "status": "PLACED",
      "shippingAddress": "123 Main St, Apt 4B, San Francisco, CA 94102",
      "placedAt": "2026-02-25T10:15:30Z",
      "cancelledAt": null,
      "shippedAt": null,
      "trackingNumber": null
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 1,
  "totalPages": 1
}
```

---

*Next: [dependencies.md](./dependencies.md) -- full build file and database schemas.*
