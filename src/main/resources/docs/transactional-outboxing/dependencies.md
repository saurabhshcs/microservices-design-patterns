# Transactional Outbox -- Dependencies and Infrastructure

---

## Dependency Summary

| Dependency | Purpose | Why This Choice |
|-----------|---------|-----------------|
| Spring Boot Starter Web | REST controllers, Jackson JSON | Standard REST API framework |
| Spring Boot Starter Data JPA | ORM, repository abstraction, `@Transactional` | The outbox pattern requires atomic writes -- JPA transactions provide this |
| Spring Boot Starter Validation | Bean validation on command objects | Declarative input validation |
| Spring Kafka | Kafka producer integration | The outbox relay publishes events to Kafka |
| PostgreSQL Driver | JDBC connectivity | Supports `FOR UPDATE SKIP LOCKED` for safe concurrent polling |
| Jackson Databind | JSON serialisation of event payloads | Already included via Spring Web; used explicitly in PaymentService |
| Lombok | Boilerplate reduction | Consistent with project conventions |
| Spring Boot Starter Test | JUnit 5, Mockito, Spring Test | Standard test stack |
| Testcontainers (PostgreSQL) | Real database for integration tests | Tests outbox writes against actual PostgreSQL |
| Testcontainers (Kafka) | Real Kafka broker for integration tests | Tests end-to-end: service -> outbox -> poller -> Kafka |

---

## Gradle Build File

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.saurabhshcs.adtech'
version = '0.0.1-SNAPSHOT'
description = 'Payment Service with Transactional Outbox'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Core Spring Boot ---
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // --- Messaging ---
    implementation 'org.springframework.kafka:spring-kafka'

    // --- Database ---
    runtimeOnly 'org.postgresql:postgresql'

    // --- Developer Experience ---
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // --- Testing ---
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.testcontainers:postgresql:1.20.4'
    testImplementation 'org.testcontainers:kafka:1.20.4'
    testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

---

## Maven Equivalent (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
        <relativePath/>
    </parent>

    <groupId>com.saurabhshcs.adtech</groupId>
    <artifactId>payment-outbox-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Payment Service with Transactional Outbox</name>

    <properties>
        <java.version>17</java.version>
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

    <dependencies>
        <!-- Core Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Messaging -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Developer Experience -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Dependency Justifications

### Spring Kafka
- **What:** Provides `KafkaTemplate` for producing messages, `@KafkaListener` for consuming, and auto-configuration for Kafka producer/consumer factories.
- **Why chosen:** Apache Kafka is the de-facto standard for event streaming in microservice architectures. Spring Kafka integrates seamlessly with Spring Boot's auto-configuration and transaction management.
- **Alternative considered:** RabbitMQ (via Spring AMQP). Rejected because Kafka's log-based architecture provides better durability, replay capability, and throughput for event-driven systems. Kafka's partitioning model also aligns well with the outbox pattern's `aggregate_id`-based ordering.
- **Alternative considered:** Apache Pulsar. Rejected due to smaller ecosystem and less mature Spring integration.

### PostgreSQL Driver
- **What:** JDBC driver for PostgreSQL.
- **Why chosen specifically for outbox:**
  1. **`FOR UPDATE SKIP LOCKED`**: Essential for safe concurrent polling. Multiple poller instances can run without processing the same row twice. MySQL supports this too (since 8.0), but PostgreSQL's implementation is more battle-tested.
  2. **WAL (Write-Ahead Log)**: If migrating to Debezium CDC later, PostgreSQL's logical replication protocol (`pgoutput`) is natively supported by Debezium without plugins.
  3. **JSONB columns**: The payload column could be stored as JSONB for indexed querying if needed.

### Jackson Databind
- **What:** JSON serialisation/deserialisation library.
- **Why chosen:** Already included transitively via `spring-boot-starter-web`. Used explicitly in `PaymentService` to serialise the `PaymentCompletedEvent` record into the outbox table's `payload` column.
- **Alternative considered:** GSON. Rejected because Jackson is the Spring Boot default and has better record class support in Java 17.

### Spring Kafka Test
- **What:** Provides `EmbeddedKafkaBroker` for testing Kafka producers/consumers without external infrastructure.
- **Why chosen:** Allows unit tests to verify the full outbox -> poller -> Kafka flow. Combined with Testcontainers Kafka for integration tests.

### Testcontainers (Kafka)
- **What:** Manages a Kafka container (based on Confluent or RedPanda images) for integration testing.
- **Why chosen:** `EmbeddedKafkaBroker` is useful for fast unit tests, but Testcontainers Kafka tests against a real broker, catching configuration and serialisation issues that embedded mode might miss.

---

## Infrastructure Choices

### Why Apache Kafka (not RabbitMQ or SQS)

| Criterion | Apache Kafka | RabbitMQ | Amazon SQS |
|-----------|-------------|----------|------------|
| Durability | Log-based, configurable retention | Message acknowledged = removed | 14-day retention |
| Replay capability | Consumers can reset offsets | Not natively supported | Dead-letter queues only |
| Ordering | Per-partition ordering | Per-queue FIFO (limited) | FIFO queues (limited throughput) |
| Throughput | Millions of messages/sec | Hundreds of thousands/sec | Thousands/sec per queue |
| Debezium integration | Native (Kafka Connect) | Requires additional bridge | Not supported |
| Consumer groups | Native, automatic rebalancing | Manual with exchanges/bindings | Not applicable |

**Decision:** Kafka wins for the outbox pattern because:
1. Per-partition ordering maps directly to per-aggregate ordering (partition key = aggregate_id).
2. Consumers can replay events by resetting offsets -- essential for rebuilding projections or debugging.
3. The path to Debezium CDC is seamless since Debezium is a Kafka Connect connector.

### Why PostgreSQL (reinforced from CQRS)

For the outbox pattern specifically, PostgreSQL provides:
1. **`FOR UPDATE SKIP LOCKED`** -- enables safe concurrent polling without row-level contention.
2. **`pgoutput` logical replication** -- native Debezium support without installing decoder plugins.
3. **Reliable auto-increment** via `GENERATED ALWAYS AS IDENTITY` -- guarantees strict ordering for the `sequence_id` column.

---

## Database Schema

```sql
-- Payment business table
CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    customer_id           UUID          NOT NULL,
    amount                NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency              VARCHAR(3)    NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_method_token  VARCHAR(255)  NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ,

    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_status      ON payments(status);


-- Outbox table -- the heart of the pattern
CREATE TABLE outbox_events (
    event_id        UUID PRIMARY KEY,
    sequence_id     BIGINT GENERATED ALWAYS AS IDENTITY,  -- strict total order
    aggregate_type  VARCHAR(100) NOT NULL,                 -- e.g., "Payment"
    aggregate_id    UUID         NOT NULL,                 -- partition key for Kafka
    event_type      VARCHAR(100) NOT NULL,                 -- e.g., "PaymentCompletedEvent"
    payload         TEXT         NOT NULL,                 -- JSON-serialised event
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT'))
);

-- Composite index for the poller's query:
-- "Give me pending events in order, and lock them"
CREATE INDEX idx_outbox_status_sequence ON outbox_events(status, sequence_id)
    WHERE status = 'PENDING';

-- Index for cleanup job
CREATE INDEX idx_outbox_sent_at ON outbox_events(sent_at)
    WHERE status = 'SENT';


-- Optional: Grant CDC user read access to the outbox table for Debezium
-- CREATE USER finflow_cdc WITH REPLICATION LOGIN PASSWORD 'cdc_password';
-- GRANT SELECT ON outbox_events TO finflow_cdc;
-- CREATE PUBLICATION outbox_publication FOR TABLE outbox_events;
```

### Schema Design Rationale

| Column | Why |
|--------|-----|
| `event_id` (UUID, PK) | Globally unique identifier used by consumers for idempotency. UUID avoids coordination between services. |
| `sequence_id` (BIGINT, auto-increment) | Provides a strict total order for the poller. UUIDs are not ordered, so we need a monotonically increasing value. |
| `aggregate_type` | Used to derive the Kafka topic name. Allows a single outbox table to serve multiple aggregate types. |
| `aggregate_id` | Used as the Kafka partition key. All events for the same aggregate land on the same partition, preserving per-aggregate ordering. |
| `event_type` | Stored as a Kafka header so consumers can route or filter events without deserialising the payload. |
| `payload` (TEXT) | JSON-serialised event data. TEXT is portable across databases. Could be JSONB in PostgreSQL for indexed queries. |
| `status` | Simple state machine: PENDING -> SENT. The partial index on `status = 'PENDING'` makes the poller's query fast. |
| `created_at` | Audit trail: when was the event written. |
| `sent_at` | Audit trail + cleanup job filter: when was it published. |

---

## Kafka Topic Configuration

```bash
# Create the payment-events topic with appropriate settings
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic payment-events \
  --partitions 6 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# Partitions: 6 allows up to 6 consumer instances in a consumer group.
# Replication factor: 3 for durability (survives loss of 1 broker).
# Retention: 7 days (matches outbox cleanup retention).
# min.insync.replicas: 2 ensures writes are acknowledged by at least 2 replicas.
```

---

## Docker Compose (Local Development)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: finflow
      POSTGRES_USER: finflow_app
      POSTGRES_PASSWORD: dev_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./schema.sql:/docker-entrypoint-initdb.d/01-schema.sql

  kafka:
    image: confluentinc/cp-kafka:7.7.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,HOST://localhost:9092
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      KAFKA_LISTENERS: PLAINTEXT://kafka:29092,CONTROLLER://kafka:29093,HOST://0.0.0.0:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk

volumes:
  postgres_data:
```

---

## Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17 | Minimum for Spring Boot 4.x |
| Spring Boot | 4.0.0 | Jakarta EE 10 namespace |
| Spring Kafka | 4.0.x (managed by Boot) | Compatible with Kafka broker 3.x+ |
| Apache Kafka (broker) | 3.7+ | Required for KRaft mode (no ZooKeeper) |
| PostgreSQL | 15+ | `FOR UPDATE SKIP LOCKED` available since 9.5; 15+ for performance |
| Debezium (optional) | 2.7.x | Requires Kafka Connect 3.x; supports PostgreSQL `pgoutput` |
| Testcontainers | 1.20.4 | Requires Docker 20.10+ |

---

## Best Practices for Infrastructure Setup

1. **Use KRaft mode for Kafka (no ZooKeeper).** Kafka 3.7+ supports KRaft natively, reducing operational complexity.
2. **Set `min.insync.replicas=2` on the Kafka topic.** Combined with `acks=all`, this ensures writes survive the loss of one broker.
3. **Configure replication factor of 3.** Standard for production Kafka clusters -- survives loss of one broker.
4. **Use separate database users for application and CDC.** The CDC user needs `REPLICATION` privileges; the application user should not have them.
5. **Monitor outbox table size.** Set alerts on `SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'`. A growing count indicates relay failure.

---

*Last updated: 2026-02-28*

*Back to: [README.md](./README.md) | [scenario.md](./scenario.md) | [implementation.md](./implementation.md)*
