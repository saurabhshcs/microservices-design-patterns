# Transactional Outbox -- Implementation

> **Stack:** Spring Boot 4.0.0, Java 17, Spring Data JPA, PostgreSQL, Spring Kafka, Lombok
> **Pattern variant:** Polling-based outbox relay with scheduled task

---

## Table of Contents

1. [Domain Model -- Payment Entity](#1-domain-model----payment-entity)
2. [Outbox Event Entity](#2-outbox-event-entity)
3. [Domain Events](#3-domain-events)
4. [Command Object](#4-command-object)
5. [Repositories](#5-repositories)
6. [Payment Service (Atomic Write)](#6-payment-service-atomic-write)
7. [Outbox Relay / Poller](#7-outbox-relay--poller)
8. [Kafka Configuration](#8-kafka-configuration)
9. [REST Controller](#9-rest-controller)
10. [Outbox Cleanup Job](#10-outbox-cleanup-job)
11. [Application Properties](#11-application-properties)
12. [Debezium Alternative (Reference)](#12-debezium-alternative-reference)

---

## 1. Domain Model -- Payment Entity

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The Payment aggregate root.
 * <p>
 * Represents a single payment transaction in the FinFlow system.
 * Immutable after creation -- status changes are modelled as new events,
 * not in-place updates, aligning with event-driven design.
 * </p>
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String paymentMethodToken;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    /**
     * Factory method to create a completed payment.
     * In a real system, this would first create a PENDING payment,
     * call the payment processor, then transition to COMPLETED.
     */
    public static Payment createCompleted(UUID customerId,
                                          BigDecimal amount,
                                          String currency,
                                          String paymentMethodToken) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.customerId = customerId;
        payment.amount = amount;
        payment.currency = currency;
        payment.paymentMethodToken = paymentMethodToken;
        payment.status = PaymentStatus.COMPLETED;
        payment.createdAt = Instant.now();
        payment.completedAt = Instant.now();
        return payment;
    }
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.domain;

/**
 * Lifecycle states of a payment.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
```

---

## 2. Outbox Event Entity

This is the heart of the pattern. The outbox table stores events that need to be published to Kafka.

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single outbound event stored in the outbox table.
 * <p>
 * Written in the same database transaction as the business data change.
 * The {@link OutboxPoller} reads pending rows and publishes them to Kafka.
 * </p>
 *
 * <p><strong>Column design rationale:</strong></p>
 * <ul>
 *   <li>{@code aggregateType} + {@code aggregateId}: Used as the Kafka topic
 *       and partition key, respectively. This ensures per-aggregate ordering.</li>
 *   <li>{@code eventType}: Allows consumers to route/filter events without
 *       deserialising the payload.</li>
 *   <li>{@code payload}: JSON-serialised event data. Stored as TEXT for
 *       portability across databases.</li>
 *   <li>{@code sequenceId}: Auto-incremented by the database. Guarantees
 *       a strict total order for the poller.</li>
 *   <li>{@code status}: PENDING (not yet sent) or SENT (acknowledged by Kafka).</li>
 * </ul>
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_sequence", columnList = "status, sequenceId")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "payload")
public class OutboxEvent {

    @Id
    private UUID eventId;

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long sequenceId;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    /**
     * Factory method to create a new pending outbox event.
     *
     * @param aggregateType the type of aggregate (e.g., "Payment")
     * @param aggregateId   the aggregate identifier (used as Kafka partition key)
     * @param eventType     the event class name (e.g., "PaymentCompletedEvent")
     * @param payload       JSON-serialised event data
     * @return a new PENDING outbox event
     */
    public static OutboxEvent create(String aggregateType,
                                     UUID aggregateId,
                                     String eventType,
                                     String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.createdAt = Instant.now();
        return event;
    }

    /**
     * Mark this event as successfully sent to the message broker.
     */
    public void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = Instant.now();
    }
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox;

/**
 * Status of an outbox event row.
 */
public enum OutboxEventStatus {
    /** Not yet published to the message broker. */
    PENDING,
    /** Successfully published and acknowledged. */
    SENT
}
```

---

## 3. Domain Events

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a payment completes successfully.
 * Serialised to JSON and stored in the outbox table's payload column.
 */
public record PaymentCompletedEvent(
        UUID eventId,
        UUID paymentId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        Instant completedAt
) {}
```

---

## 4. Command Object

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to process a new payment.
 *
 * @param customerId         the customer initiating the payment
 * @param amount             the payment amount (must be > 0)
 * @param currency           ISO 4217 currency code (e.g., "USD", "EUR")
 * @param paymentMethodToken tokenised payment method from the frontend
 */
public record ProcessPaymentCommand(
        @NotNull UUID customerId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) String currency,
        @NotNull String paymentMethodToken
) {}
```

---

## 5. Repositories

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.repository;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the Payment aggregate.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
```

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.repository;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox.OutboxEvent;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the outbox event table.
 * Contains specialised queries for the poller and the cleanup job.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetch the next batch of pending events in strict order.
     * The {@code LIMIT} is controlled by the caller via Pageable or a native query.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY sequence_id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingEventsForUpdate(int batchSize);

    /**
     * Delete sent events older than the given cutoff timestamp.
     * Used by the cleanup job to prevent unbounded table growth.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.sentAt < :cutoff")
    int deleteSentEventsBefore(OutboxEventStatus status, Instant cutoff);
}
```

> **Note on `FOR UPDATE SKIP LOCKED`:** This PostgreSQL clause locks the selected rows so that concurrent poller instances (in a horizontally scaled deployment) do not process the same row twice. Rows locked by another transaction are skipped, not waited on.

---

## 6. Payment Service (Atomic Write)

This is the critical component. The business data and the outbox event are written in the **same transaction**.

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.command.ProcessPaymentCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.domain.Payment;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.event.PaymentCompletedEvent;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox.OutboxEvent;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.repository.OutboxEventRepository;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Processes payments and writes the outbox event in the same transaction.
 * <p>
 * <strong>This is the core of the Transactional Outbox pattern.</strong>
 * By inserting the outbox row within the same {@code @Transactional} boundary
 * as the payment row, we guarantee that either both are persisted or neither is.
 * The outbox relay handles delivery to Kafka asynchronously.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process a payment and write the corresponding outbox event atomically.
     *
     * @param command the payment command
     * @return the generated payment ID
     * @throws PaymentProcessingException if JSON serialisation fails
     */
    @Transactional
    public UUID processPayment(ProcessPaymentCommand command) {
        log.info("Processing payment for customer {} -- {} {}",
                command.customerId(), command.amount(), command.currency());

        // Step 1: Create and persist the payment
        Payment payment = Payment.createCompleted(
                command.customerId(),
                command.amount(),
                command.currency(),
                command.paymentMethodToken()
        );
        paymentRepository.save(payment);
        log.debug("Payment {} persisted", payment.getId());

        // Step 2: Create the domain event
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                payment.getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                Instant.now()
        );

        // Step 3: Serialise and write to the outbox table (SAME TRANSACTION)
        String payload = serialiseEvent(event);
        OutboxEvent outboxEvent = OutboxEvent.create(
                "Payment",
                payment.getId(),
                "PaymentCompletedEvent",
                payload
        );
        outboxEventRepository.save(outboxEvent);
        log.debug("Outbox event {} written for payment {}", outboxEvent.getEventId(), payment.getId());

        // Both the payment and the outbox event commit together.
        // If anything fails, both are rolled back.
        return payment.getId();
    }

    private String serialiseEvent(PaymentCompletedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new PaymentProcessingException(
                    "Failed to serialise PaymentCompletedEvent for payment " + event.paymentId(), e);
        }
    }

    /**
     * Unchecked exception for payment processing failures.
     */
    public static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

---

## 7. Outbox Relay / Poller

The poller runs on a fixed schedule, reads pending outbox rows, sends them to Kafka, and marks them as sent.

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.relay;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox.OutboxEvent;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled component that polls the outbox table and relays events to Kafka.
 * <p>
 * Runs every {@code outbox.poller.interval-ms} milliseconds (default 500ms).
 * Each cycle:
 * <ol>
 *   <li>Fetches up to {@code outbox.poller.batch-size} PENDING rows with
 *       {@code FOR UPDATE SKIP LOCKED} to prevent concurrent processing.</li>
 *   <li>Sends each event to the Kafka topic derived from the aggregate type.</li>
 *   <li>On successful Kafka acknowledgement, marks the row as SENT.</li>
 *   <li>On failure, logs the error and leaves the row as PENDING for retry.</li>
 * </ol>
 * </p>
 *
 * <p><strong>Idempotency note:</strong> If the poller crashes after Kafka ack
 * but before marking the row SENT, the event will be re-published on the next
 * cycle. Consumers must deduplicate using the {@code eventId} in the payload.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.poller.batch-size:100}")
    private int batchSize;

    @Value("${outbox.kafka.topic-prefix:}")
    private String topicPrefix;

    /**
     * Poll the outbox table and relay pending events to Kafka.
     * The interval is configurable via {@code outbox.poller.interval-ms}.
     */
    @Scheduled(fixedDelayString = "${outbox.poller.interval-ms:500}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForUpdate(batchSize);

        if (pendingEvents.isEmpty()) {
            return;  // Nothing to process
        }

        log.info("Outbox poller found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = resolveTopic(event.getAggregateType());
                String key = event.getAggregateId().toString();

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic,
                        key,
                        event.getPayload()
                );

                // Add event metadata as Kafka headers for consumer routing
                record.headers()
                        .add("eventId", event.getEventId().toString().getBytes())
                        .add("eventType", event.getEventType().getBytes())
                        .add("aggregateType", event.getAggregateType().getBytes());

                // Synchronous send -- blocks until Kafka acknowledges
                kafkaTemplate.send(record).get();

                event.markSent();
                outboxEventRepository.save(event);
                log.debug("Outbox event {} published to topic {}", event.getEventId(), topic);

            } catch (Exception e) {
                // Log and skip -- the event stays PENDING for the next cycle
                log.error("Failed to publish outbox event {} to Kafka: {}",
                        event.getEventId(), e.getMessage(), e);
                // Do NOT rethrow -- we want to continue processing other events
            }
        }
    }

    /**
     * Derive the Kafka topic name from the aggregate type.
     * Convention: lowercase-aggregate-type + "-events"
     * Example: "Payment" -> "payment-events"
     */
    private String resolveTopic(String aggregateType) {
        String topic = aggregateType.toLowerCase() + "-events";
        return topicPrefix.isEmpty() ? topic : topicPrefix + "." + topic;
    }
}
```

---

## 8. Kafka Configuration

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the outbox relay.
 * <p>
 * Key configuration decisions:
 * <ul>
 *   <li>{@code acks=all}: Wait for all in-sync replicas to acknowledge.
 *       This provides the strongest durability guarantee.</li>
 *   <li>{@code enable.idempotence=true}: Prevents duplicate messages caused
 *       by producer retries (exactly-once semantics at the Kafka level).</li>
 *   <li>{@code retries=3}: The producer retries transient failures.
 *       Combined with the poller's retry-on-next-cycle approach, this provides
 *       robust delivery.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableScheduling
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Durability: wait for all replicas to acknowledge
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent producer: prevents duplicates from retries at the broker level
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retry transient errors (network blips, leader election)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // Batch small messages for throughput (tuned for outbox relay workload)
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

---

## 9. REST Controller

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.controller;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.command.ProcessPaymentCommand;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for payment operations.
 * Thin layer -- delegates to {@link PaymentService} for business logic.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Process a new payment.
     *
     * @param command the payment details
     * @return 201 Created with the payment URI in the Location header
     */
    @PostMapping
    public ResponseEntity<Void> processPayment(@Valid @RequestBody ProcessPaymentCommand command) {
        UUID paymentId = paymentService.processPayment(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(paymentId)
                .toUri();
        return ResponseEntity.created(location).build();
    }
}
```

---

## 10. Outbox Cleanup Job

Sent events accumulate in the outbox table. This scheduled job removes old entries.

```java
package com.saurabhshcs.adtech.microservices.designpattern.outbox.relay;

import com.saurabhshcs.adtech.microservices.designpattern.outbox.outbox.OutboxEventStatus;
import com.saurabhshcs.adtech.microservices.designpattern.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodic cleanup of sent outbox events.
 * <p>
 * Retains sent events for a configurable duration (default 7 days) for
 * debugging and audit purposes, then deletes them to prevent unbounded
 * table growth.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${outbox.cleanup.retention-days:7}")
    private int retentionDays;

    /**
     * Runs daily at 2:00 AM to clean up old sent events.
     */
    @Scheduled(cron = "${outbox.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void cleanupSentEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = outboxEventRepository.deleteSentEventsBefore(OutboxEventStatus.SENT, cutoff);
        log.info("Outbox cleanup: deleted {} sent events older than {} days", deleted, retentionDays);
    }
}
```

---

## 11. Application Properties

```yaml
# application.yml -- Transactional Outbox service configuration

spring:
  application:
    name: payment-outbox-service

  datasource:
    url: jdbc:postgresql://localhost:5432/finflow
    username: finflow_app
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false

  kafka:
    bootstrap-servers: localhost:9092

# --- Outbox poller configuration ---
outbox:
  poller:
    interval-ms: 500          # Poll every 500ms
    batch-size: 100            # Process up to 100 events per cycle
  kafka:
    topic-prefix: ""           # Optional prefix for multi-tenant setups
  cleanup:
    retention-days: 7          # Keep sent events for 7 days
    cron: "0 0 2 * * *"       # Run cleanup at 2:00 AM daily

server:
  port: 8082

logging:
  level:
    com.saurabhshcs.adtech.microservices.designpattern.outbox: DEBUG
    org.apache.kafka: WARN
```

---

## 12. Debezium Alternative (Reference)

For higher-throughput systems (>100 events/sec), replace the polling approach with Debezium CDC. Debezium tails the PostgreSQL Write-Ahead Log (WAL) and pushes changes directly to Kafka -- no application-level polling required.

### Debezium Connector Configuration (JSON)

```json
{
  "name": "finflow-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "localhost",
    "database.port": "5432",
    "database.user": "finflow_cdc",
    "database.password": "${DB_CDC_PASSWORD}",
    "database.dbname": "finflow",
    "database.server.name": "finflow",

    "table.include.list": "public.outbox_events",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType",
    "transforms.outbox.table.field.event.id": "event_id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",

    "tombstones.on.delete": false,
    "plugin.name": "pgoutput",
    "slot.name": "outbox_slot",
    "publication.name": "outbox_publication"
  }
}
```

### Key Differences with Debezium

| Aspect | Polling | Debezium CDC |
|--------|---------|-------------|
| Latency | ~500ms (polling interval) | ~10ms (WAL tailing) |
| Database load | Periodic SELECT queries | Reads WAL (no table queries) |
| Cleanup | Application-level job | Debezium manages offsets; application still needs cleanup |
| Infrastructure | None beyond the application | Kafka Connect cluster + Debezium connector |
| Ordering | Application ensures via `sequence_id` | WAL position provides ordering |
| Deployment | Part of the application | Separate operational component |

---

## Example API Payloads

### Process a Payment

**Request:**

```http
POST /api/payments HTTP/1.1
Content-Type: application/json

{
  "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 149.99,
  "currency": "USD",
  "paymentMethodToken": "tok_visa_4242"
}
```

**Response:**

```http
HTTP/1.1 201 Created
Location: /api/payments/d7e8f9a0-b1c2-3d4e-5f6a-7b8c9d0e1f2a
```

### Kafka Message Published by Outbox Relay

**Topic:** `payment-events`
**Key:** `d7e8f9a0-b1c2-3d4e-5f6a-7b8c9d0e1f2a`
**Headers:**
```
eventId: 550e8400-e29b-41d4-a716-446655440000
eventType: PaymentCompletedEvent
aggregateType: Payment
```
**Value:**
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "paymentId": "d7e8f9a0-b1c2-3d4e-5f6a-7b8c9d0e1f2a",
  "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 149.99,
  "currency": "USD",
  "completedAt": "2026-02-25T14:30:00Z"
}
```

---

*Next: [dependencies.md](./dependencies.md) -- full build file, database schemas, and infrastructure setup.*
