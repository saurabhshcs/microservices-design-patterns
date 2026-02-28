# API Gateway -- Dependencies and Infrastructure

---

## Dependency Summary

| Dependency | Purpose | Why This Choice |
|-----------|---------|-----------------|
| Spring Cloud Gateway | Reactive API gateway with routing, filters | Non-blocking, high-throughput gateway built on Project Reactor |
| Spring Boot Starter Security | JWT validation, CORS, OAuth2 resource server | Integrates with Spring Cloud Gateway's reactive stack |
| Spring Boot Starter OAuth2 Resource Server | JWT decoding with JWKS endpoint support | Industry-standard JWT validation without custom crypto code |
| Spring Boot Starter Data Redis Reactive | Redis client for rate limiting | Reactive Redis client compatible with Gateway's non-blocking model |
| Spring Cloud Circuit Breaker Resilience4j | Circuit breaker integration | Mature, lightweight, and actively maintained (replaces Hystrix) |
| Resilience4j Spring Boot 3 | Auto-configuration for Resilience4j | Provides circuit breaker metrics, health indicators, and Actuator integration |
| Spring Boot Starter Actuator | Health checks, metrics, gateway endpoint | Exposes circuit breaker states and gateway routes for monitoring |
| Lombok | Boilerplate reduction | Consistent with project conventions |
| Spring Boot Starter Test | JUnit 5, WebTestClient | Reactive test client for gateway integration tests |

---

## Gradle Build File

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

// Spring Cloud BOM -- manages Spring Cloud dependency versions
ext {
    set('springCloudVersion', "2024.0.0")
}

group = 'com.saurabhshcs.adtech'
version = '0.0.1-SNAPSHOT'
description = 'API Gateway for RetailHub Platform'

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

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

dependencies {
    // --- Spring Cloud Gateway (reactive) ---
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'

    // --- Security: JWT / OAuth2 ---
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

    // --- Rate Limiting (Redis) ---
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

    // --- Circuit Breaker (Resilience4j) ---
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'

    // --- Observability ---
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // --- Developer Experience ---
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // --- Testing ---
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
    testImplementation 'com.redis:testcontainers-redis:2.2.2'
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
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>API Gateway for RetailHub Platform</name>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2024.0.0</spring-cloud.version>
        <testcontainers.version>1.20.4</testcontainers.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Cloud Gateway -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Security: JWT / OAuth2 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <!-- Rate Limiting (Redis) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Circuit Breaker (Resilience4j) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
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

### Spring Cloud Gateway
- **What:** A reactive API gateway built on Spring WebFlux and Project Reactor.
- **Why chosen over Netflix Zuul:** Zuul 1 is blocking (servlet-based) and Netflix has deprioritised Zuul 2. Spring Cloud Gateway is the official Spring recommendation, is fully reactive, and handles thousands of concurrent connections with minimal threads.
- **Why chosen over Kong/Nginx:** Kong requires Lua for custom logic. Spring Cloud Gateway allows writing filters in Java, which is the team's primary language. The Spring ecosystem integration (Security, Actuator, Resilience4j) is seamless.
- **Why chosen over AWS API Gateway:** AWS API Gateway is managed but locks you into AWS. RetailHub needs a self-hosted, cloud-agnostic solution.
- **Important:** Spring Cloud Gateway uses WebFlux (reactive). Do NOT add `spring-boot-starter-web` alongside it -- the two stacks are mutually exclusive.

### Spring Boot Starter OAuth2 Resource Server
- **What:** Provides JWT decoding with automatic JWKS endpoint caching and key rotation support via Nimbus JOSE+JWT.
- **Why chosen:** Avoids writing custom JWT parsing logic. Automatically fetches and caches signing keys from the auth server's JWKS endpoint. Handles key rotation transparently.
- **Alternative considered:** Manual JWT parsing with `io.jsonwebtoken:jjwt`. Rejected because it requires manual key management, no JWKS support, and more boilerplate.

### Spring Boot Starter Data Redis Reactive
- **What:** Reactive Lettuce-based Redis client.
- **Why chosen:** Spring Cloud Gateway's built-in `RequestRateLimiter` filter requires a reactive `ReactiveRedisTemplate`. The rate limit counters must be stored in Redis (not in-memory) because the gateway runs multiple instances behind a load balancer -- all instances must share the same counter.
- **Why Redis over in-memory rate limiting:** In-memory counters do not work in a horizontally scaled deployment. With 3 gateway instances, each would track its own counter, allowing 3x the intended rate limit.

### Spring Cloud Circuit Breaker (Resilience4j)
- **What:** Integrates Resilience4j circuit breakers with Spring Cloud's `ReactiveCircuitBreakerFactory`.
- **Why Resilience4j over Hystrix:** Netflix Hystrix is in maintenance mode (deprecated since 2018). Resilience4j is the recommended replacement -- it is lightweight, modular, and has first-class support for reactive streams.
- **Why Resilience4j over Sentinel:** Alibaba Sentinel is powerful but has a smaller Western community and less Spring Cloud integration documentation.
- **Key features used:**
  - `CircuitBreaker`: Prevents cascading failures.
  - `TimeLimiter`: Sets a maximum wait time for backend responses.
  - Health indicators exposed via Actuator for monitoring dashboards.

### Spring Boot Starter Actuator
- **What:** Production-ready features: health checks, metrics, and operational endpoints.
- **Why needed:** Exposes `/actuator/gateway/routes` (all registered routes), `/actuator/circuitbreakers` (circuit breaker states), and `/actuator/health` (overall gateway health). Essential for operations teams to monitor the gateway.

---

## Infrastructure Choices

### Why Redis (for Rate Limiting)

| Criterion | Redis | In-Memory (Caffeine) | PostgreSQL |
|-----------|-------|---------------------|------------|
| Distributed counters | Yes (shared across instances) | No (per-instance only) | Yes (but slow) |
| Latency | Sub-millisecond | Sub-microsecond | ~1ms (over network) |
| TTL support | Native (`EXPIRE`) | Yes | Requires manual cleanup |
| Atomic increment | `INCR` command (single-threaded) | `AtomicLong` (per-JVM) | Row locking required |
| Horizontal scaling | Redis Cluster, Sentinel | Not applicable | Read replicas (overkill) |
| Operational overhead | Moderate (one more service) | None | Already exists but wrong tool |

**Decision:** Redis is the standard choice for distributed rate limiting. Its single-threaded execution model guarantees atomic counter operations without locking. Sub-millisecond latency means the rate limit check adds negligible overhead to gateway routing.

### Why Spring Cloud Gateway is Reactive

The API Gateway is fundamentally an I/O-bound application. It receives a request, validates it, and proxies it to a backend service. The gateway thread does almost no computation -- it spends most of its time waiting for:
1. Redis (rate limit check)
2. Backend service response
3. JWKS endpoint (JWT key refresh)

A **reactive** (non-blocking) architecture handles these waits efficiently:
- **Servlet/blocking model** (Spring MVC): Each request occupies a thread. At 5,000 concurrent requests, you need 5,000 threads (high memory, context-switching overhead).
- **Reactive model** (Spring WebFlux): Non-blocking I/O with an event loop. The same 5,000 concurrent requests are handled by ~8 threads (number of CPU cores).

This is why Spring Cloud Gateway is built on WebFlux, not MVC.

---

## Infrastructure Setup

### Docker Compose (Local Development)

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru

  # Mock OAuth2 authorization server for local development
  mock-auth-server:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    ports:
      - "8090:8090"
    environment:
      SERVER_PORT: 8090
      JSON_CONFIG: >
        {
          "interactiveLogin": false,
          "tokenCallbacks": [
            {
              "issuerId": "retailhub",
              "tokenExpiry": 3600,
              "requestMappings": [
                {
                  "requestParam": "grant_type",
                  "match": "client_credentials",
                  "claims": {
                    "sub": "test-user-123",
                    "roles": ["ROLE_CUSTOMER"]
                  }
                }
              ]
            }
          ]
        }

  # Backend service stubs (for gateway testing)
  product-service:
    image: wiremock/wiremock:3.9.1
    ports:
      - "8081:8080"
    volumes:
      - ./wiremock/product-service:/home/wiremock

  user-service:
    image: wiremock/wiremock:3.9.1
    ports:
      - "8082:8080"
    volumes:
      - ./wiremock/user-service:/home/wiremock

  order-service:
    image: wiremock/wiremock:3.9.1
    ports:
      - "8083:8080"
    volumes:
      - ./wiremock/order-service:/home/wiremock
```

### WireMock Stub Example (Product Service)

Create `wiremock/product-service/mappings/get-products.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": "/api/products.*"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "content": [
        {
          "id": "p-001",
          "name": "Wireless Keyboard",
          "price": 49.99,
          "category": "Electronics"
        }
      ],
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

---

## Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17 | Minimum for Spring Boot 4.x |
| Spring Boot | 4.0.0 | Jakarta EE 10 namespace |
| Spring Cloud | 2024.0.0 | Aligned with Spring Boot 4.x |
| Spring Cloud Gateway | 5.0.x (managed by Cloud BOM) | Reactive only; do NOT mix with `spring-boot-starter-web` |
| Resilience4j | 2.2.x (managed by Cloud BOM) | Compatible with Spring Boot 3.x+ |
| Redis | 7.x | Recommended for performance; compatible with 6.x |
| Nimbus JOSE+JWT | 9.x (managed by Spring Security) | JWT decoding library |
| Lombok | 1.18.34 (managed by Boot) | Annotation processor |
| Testcontainers | 1.20.4 | Requires Docker 20.10+ |

---

## Important Compatibility Notes

1. **Do NOT add `spring-boot-starter-web`** to a Spring Cloud Gateway project. The gateway runs on WebFlux (Netty). Adding the servlet stack will cause startup failures.

2. **Redis must be available** when rate limiting is enabled. If Redis is down, the `RequestRateLimiter` filter will deny all requests by default. To change this behaviour, set `spring.cloud.gateway.redis-rate-limiter.deny-empty-key=false`.

3. **JWKS caching:** The `NimbusJwtDecoder` caches signing keys from the JWKS endpoint. Key rotation is handled transparently, but the first request after rotation may take ~100ms longer.

4. **Circuit breaker naming:** The `name` in route YAML configuration must match the Resilience4j instance name in `resilience4j.circuitbreaker.instances.*`.

---

## Best Practices for Infrastructure Setup

1. **Run Redis with `maxmemory-policy allkeys-lru`.** Rate limit keys are ephemeral -- eviction is acceptable and prevents Redis from running out of memory.
2. **Use mock OAuth2 server for local development.** The `mock-oauth2-server` Docker image generates valid JWT tokens without requiring a production auth server.
3. **Configure `deny-empty-key=false` for rate limiter.** By default, if the key resolver returns empty, all requests are denied. Set this to `false` for graceful degradation when Redis is unavailable.
4. **Set `spring.cloud.gateway.redis-rate-limiter.include-headers=true`.** This returns `X-RateLimit-Remaining` and `X-RateLimit-Burst-Capacity` headers to clients, helping them implement backoff.
5. **Use Actuator's gateway endpoint for route debugging.** `GET /actuator/gateway/routes` shows all registered routes with their predicates and filters. Essential for troubleshooting routing issues.

---

*Last updated: 2026-02-28*

*Back to: [README.md](./README.md) | [scenario.md](./scenario.md) | [implementation.md](./implementation.md)*
