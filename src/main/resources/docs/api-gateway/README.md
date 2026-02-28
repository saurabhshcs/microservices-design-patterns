# API Gateway Pattern

**One-line description:** Provide a single entry point for all client requests, centralising cross-cutting concerns like routing, authentication, rate limiting, and circuit breaking so that backend microservices remain focused on business logic.

---

## When to Use This Pattern

- Multiple backend microservices need to be exposed through a **unified API** to clients (web, mobile, third-party).
- Cross-cutting concerns (**authentication**, **rate limiting**, **logging**, **CORS**) are duplicated across services and should be centralised.
- Clients should not need to know about internal service topology, hostnames, or ports.
- You need **protocol translation** (e.g., external clients use REST while internal services communicate via gRPC).
- Different clients (mobile vs. web) need **different API compositions** (Backend-for-Frontend / BFF pattern).
- You want to introduce **circuit breaking** to prevent cascading failures when a downstream service is unhealthy.

## When NOT to Use This Pattern

- You have a single microservice (a gateway adds latency and complexity with no benefit).
- Your infrastructure already provides gateway capabilities (e.g., AWS API Gateway, Istio service mesh) and you do not need application-level logic in the gateway.
- Your team lacks the operational maturity to manage an additional infrastructure component.

---

## Key Components and Roles

| Component | Role |
|-----------|------|
| **Gateway Application** | Spring Cloud Gateway instance that receives all inbound requests |
| **Route Configuration** | Declarative or programmatic rules mapping URL paths to backend services |
| **JWT Authentication Filter** | Global filter that validates JWT tokens and extracts claims before routing |
| **Rate Limiter Filter** | Per-route filter that limits request throughput using Redis as a distributed counter |
| **Circuit Breaker Filter** | Per-route filter (Resilience4j) that short-circuits requests to failing services |
| **Service Discovery** | Optional integration with Eureka or Consul for dynamic route resolution |
| **Backend Services** | Product Service, User Service, Order Service -- each running on its own port |

---

## Architecture at a Glance

```
                  +-------------------+
                  |   Mobile / Web    |
                  |     Clients       |
                  +--------+----------+
                           |
                    HTTPS / port 8080
                           |
                  +--------v----------+
                  |   API Gateway     |
                  | (Spring Cloud GW) |
                  |                   |
                  | - JWT Filter      |
                  | - Rate Limiter    |
                  | - Circuit Breaker |
                  | - Route Config    |
                  +--+------+------+--+
                     |      |      |
            +--------+  +---+---+  +--------+
            |           |       |           |
   /api/products  /api/users  /api/orders
            |           |       |           |
   +--------v--+ +------v---+ +--v--------+
   | Product   | | User     | | Order     |
   | Service   | | Service  | | Service   |
   | :8081     | | :8082    | | :8083     |
   +-----------+ +----------+ +-----------+
```

---

## Documentation Index

| File | Contents |
|------|----------|
| [scenario.md](./scenario.md) | Real-world retail platform scenario with architecture diagrams |
| [implementation.md](./implementation.md) | Complete Spring Cloud Gateway implementation with JWT, rate limiting, and circuit breaking |
| [dependencies.md](./dependencies.md) | Maven/Gradle dependencies with justifications and infrastructure setup |

---

## Benefits and Trade-offs

| Benefit | Trade-off |
|---------|-----------|
| Single entry point -- clients know one hostname | Gateway is a single point of failure (mitigate with multiple instances + LB) |
| Centralised authentication and authorization | Added network hop for every request (~1-5ms latency) |
| Per-route rate limiting protects backend services | Requires Redis infrastructure for distributed rate counters |
| Circuit breaking prevents cascading failures | Gateway must be updated when new services/routes are added |
| Unified logging with correlation IDs for tracing | Can become a bottleneck if not horizontally scaled |
| Protocol translation (REST to gRPC, WebSocket) | Increased operational complexity (one more service to deploy and monitor) |

## Best Practices

1. **Never put business logic in the gateway.** The gateway handles cross-cutting concerns only: routing, authentication, rate limiting, circuit breaking, logging. Domain validation belongs in backend services.
2. **Deploy multiple gateway instances behind a load balancer.** A single gateway instance is a single point of failure. Use AWS ALB, Nginx, or HAProxy for high availability.
3. **Use Redis for rate limiting in multi-instance deployments.** In-memory counters do not work when the gateway runs as multiple instances -- each instance would track its own counter.
4. **Configure circuit breakers per route, not globally.** A failing Review Service should not affect the Product Service circuit breaker.
5. **Do NOT add `spring-boot-starter-web` to a Gateway project.** Spring Cloud Gateway is reactive (WebFlux). The two stacks are mutually exclusive and will cause startup failures.
6. **Propagate correlation IDs end-to-end.** Generate a UUID in the gateway's first filter and include it in all downstream requests and responses for distributed tracing.
7. **Tune circuit breaker thresholds per service criticality.** Payment services need stricter thresholds (30% failure rate) than review services (50%).
8. **Monitor gateway metrics via Actuator.** Expose `/actuator/gateway/routes`, `/actuator/circuitbreakers`, and `/actuator/health` for operations teams.

---

*Last updated: 2026-02-28*
