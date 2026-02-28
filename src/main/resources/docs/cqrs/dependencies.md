# CQRS -- Dependencies and Infrastructure

---

## Dependency Summary

| Dependency | Purpose | Why This Choice |
|-----------|---------|-----------------|
| Spring Boot Starter Web | REST controllers, Jackson, embedded Tomcat | Industry standard for building REST APIs in Spring |
| Spring Boot Starter Data JPA | ORM, repository abstraction, transaction management | Provides `@Transactional`, `JpaRepository`, and entity management |
| Spring Boot Starter Validation | Bean validation (`@NotNull`, `@NotEmpty`) | Declarative input validation on command objects |
| PostgreSQL Driver | JDBC connectivity to PostgreSQL | Best open-source RDBMS for transactional + analytical workloads; supports schemas for write/read separation |
| Lombok | Boilerplate reduction (`@Getter`, `@Builder`, `@Slf4j`) | Already used in the project; reduces noise in domain model code |
| Spring Boot DevTools | Live reload during development | Already in project dependencies |
| Spring Boot Starter Test | JUnit 5, Mockito, Spring Test context | Standard test stack |
| Testcontainers (PostgreSQL) | Disposable PostgreSQL containers for integration tests | Tests against a real database, not H2 -- catches SQL dialect issues |

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
description = 'CQRS Order Management Service'

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

    // --- Database ---
    runtimeOnly 'org.postgresql:postgresql'                        // PostgreSQL JDBC driver

    // --- Developer Experience ---
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // --- Testing ---
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.testcontainers:postgresql:1.20.4'
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
    <artifactId>cqrs-order-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>CQRS Order Management Service</name>

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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
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

### Spring Boot Starter Web
- **What:** Auto-configures embedded Tomcat, Spring MVC, Jackson JSON serialisation.
- **Why chosen:** The command and query controllers expose REST endpoints. Spring MVC is the de-facto standard in the Spring ecosystem.
- **Alternative considered:** Spring WebFlux (reactive). Rejected because the write side is inherently blocking (JPA transactions), and the team has more experience with servlet-based stacks.

### Spring Boot Starter Data JPA
- **What:** Provides `JpaRepository`, entity scanning, transaction management, Hibernate as the JPA implementation.
- **Why chosen:** Both the write store (normalised `Order` aggregate) and the read store (`OrderSummaryView`) are relational. JPA gives us type-safe repositories with zero boilerplate. Spring's `@Transactional` and `@TransactionalEventListener` are essential for the event-driven projection flow.
- **Alternative considered:** Spring Data JDBC (simpler, no lazy loading). Rejected because the write-side `Order` aggregate has a `@OneToMany` relationship with `OrderLineItem`, and JPA handles cascading better.

### Spring Boot Starter Validation
- **What:** Integrates Jakarta Bean Validation (Hibernate Validator) with Spring MVC.
- **Why chosen:** Command objects use `@NotNull` and `@NotEmpty` annotations for declarative validation. Without this starter, validation annotations are silently ignored.

### PostgreSQL JDBC Driver
- **What:** JDBC driver for PostgreSQL.
- **Why chosen over MySQL:** PostgreSQL supports `CREATE SCHEMA` natively, allowing us to separate the write store (`write_store` schema) and read store (`read_store` schema) within a single database instance during development. In production, these would be separate databases.
- **Why chosen over H2:** H2 is suitable only for in-memory testing. PostgreSQL behaviour (JSON types, schema support, `ON CONFLICT` for upserts) must be tested against the real engine.

### Testcontainers
- **What:** Java library that manages Docker containers for integration testing.
- **Why chosen:** Spins up a real PostgreSQL instance for each test class, ensuring SQL dialect and schema compatibility. Eliminates "works on H2 but breaks on PostgreSQL" issues.
- **Version note:** 1.20.x requires Docker or a compatible container runtime on the CI machine.

### Lombok
- **What:** Annotation processor that generates getters, setters, builders, loggers, and constructors.
- **Why chosen:** Already established in this project. Reduces boilerplate in domain entities and service classes.
- **Caution:** Avoid `@Data` on JPA entities (breaks `equals`/`hashCode` with lazy proxies). We use `@Getter` + `@NoArgsConstructor` explicitly.

---

## Infrastructure Choices

### Why PostgreSQL

| Criterion | PostgreSQL | MySQL | MongoDB |
|-----------|-----------|-------|---------|
| Schema separation (write_store / read_store) | Native `CREATE SCHEMA` | Requires separate databases | Not applicable |
| ACID transactions for write side | Full support | Full support | Multi-document transactions since 4.0 (less mature) |
| JSONB for flexible read projections | Native, indexed | JSON type (limited indexing) | Native |
| Upsert for idempotent projections | `ON CONFLICT DO UPDATE` | `INSERT ... ON DUPLICATE KEY UPDATE` | `updateOne` with upsert |
| Community & ecosystem | Excellent | Excellent | Good |
| Cost | Free, open source | Free (with caveats) | Free (Community Edition) |

**Decision:** PostgreSQL wins because schema separation allows co-locating the write and read stores during development while physically separating them in production. JSONB support gives us a future option to store the items summary as structured JSON rather than a comma-separated string.

### Why Not a Separate Database for the Read Store?

For this phase, both stores live in the same PostgreSQL instance but in separate schemas. This simplifies local development and CI/CD while maintaining a clean architectural boundary. The migration path to a separate database (or Elasticsearch) requires only:

1. Changing the `readRepository` datasource in `application.yml`.
2. Switching the projector from `@TransactionalEventListener` to a Kafka consumer.
3. No changes to controllers, handlers, or domain model.

---

## Database Schemas

### Write Store Schema

```sql
-- Write-side schema: normalised for transactional integrity

CREATE SCHEMA IF NOT EXISTS write_store;

CREATE TABLE write_store.orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PLACED',
    total_amount    NUMERIC(12,2) NOT NULL,
    shipping_address TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    cancelled_at    TIMESTAMPTZ,
    shipped_at      TIMESTAMPTZ,

    CONSTRAINT chk_order_status CHECK (status IN ('PLACED', 'CANCELLED', 'SHIPPED', 'DELIVERED'))
);

CREATE INDEX idx_orders_customer_id ON write_store.orders(customer_id);
CREATE INDEX idx_orders_status      ON write_store.orders(status);

CREATE TABLE write_store.order_line_items (
    id           UUID PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES write_store.orders(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    quantity     INT           NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),

    CONSTRAINT fk_line_item_order FOREIGN KEY (order_id) REFERENCES write_store.orders(id)
);

CREATE INDEX idx_line_items_order_id ON write_store.order_line_items(order_id);
```

### Read Store Schema

```sql
-- Read-side schema: denormalised for fast dashboard queries

CREATE SCHEMA IF NOT EXISTS read_store;

CREATE TABLE read_store.order_summary_view (
    order_id         UUID PRIMARY KEY,
    customer_id      UUID          NOT NULL,
    items_summary    VARCHAR(2000),          -- e.g. "Wireless Keyboard x2, USB-C Hub x1"
    total_items      INT           NOT NULL DEFAULT 0,
    total_amount     NUMERIC(12,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    shipping_address TEXT          NOT NULL,
    placed_at        TIMESTAMPTZ   NOT NULL,
    cancelled_at     TIMESTAMPTZ,
    shipped_at       TIMESTAMPTZ,
    tracking_number  VARCHAR(100)
);

-- Composite index for the most common query: customer order history sorted by date
CREATE INDEX idx_order_summary_customer_date
    ON read_store.order_summary_view(customer_id, placed_at DESC);

-- Index for admin status dashboards
CREATE INDEX idx_order_summary_status
    ON read_store.order_summary_view(status, placed_at DESC);
```

---

## Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17 | Minimum for Spring Boot 4.x; records, sealed classes, text blocks |
| Spring Boot | 4.0.0 | Uses Jakarta EE 10 namespace (`jakarta.*`), not `javax.*` |
| Spring Data JPA | 4.0.x (managed by Boot) | `JpaRepository` unchanged; Hibernate 7 under the hood |
| PostgreSQL | 15+ | Recommended for `MERGE` support and performance improvements |
| PostgreSQL JDBC Driver | 42.7.x (managed by Boot) | Compatible with PostgreSQL 12--17 |
| Lombok | 1.18.34 (managed by Boot) | Requires `annotationProcessor` configuration in Gradle |
| Testcontainers | 1.20.4 | Requires Docker 20.10+ or Podman |

---

## Best Practices for Dependency Management

1. **Pin Spring Boot and Spring Cloud versions together.** Spring Boot 4.0.0 should use Spring Cloud 2024.0.0. Mismatched versions cause subtle runtime errors.
2. **Use `ddl-auto: validate` in production.** Never use `create` or `update` in production. Use Flyway or Liquibase for schema migrations.
3. **Configure Hikari connection pool explicitly.** Default pool sizes are often too small for CQRS read workloads. Monitor `hikari.connections.active` and `hikari.connections.pending` metrics.
4. **Run Testcontainers tests in CI/CD.** Ensure your CI pipeline has Docker available. Tests against real PostgreSQL catch SQL dialect issues that H2 misses.

---

*Last updated: 2026-02-28*

*Back to: [README.md](./README.md) | [scenario.md](./scenario.md) | [implementation.md](./implementation.md)*
