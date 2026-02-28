# Confluence Publishing Guide

This document provides step-by-step instructions for publishing all microservice pattern documentation to Confluence.

---

## Prerequisites

Set the following environment variables before running the commands:

```bash
export CONFLUENCE_BASE_URL="https://your-instance.atlassian.net"
export CONFLUENCE_USERNAME="your-email@company.com"
export CONFLUENCE_API_TOKEN="your-api-token"
export CONFLUENCE_SPACE_KEY="ENG"                    # Your Confluence space key
export CONFLUENCE_PARENT_PAGE_ID="123456789"          # Optional: parent page ID
```

To generate an API token, visit: https://id.atlassian.com/manage-profile/security/api-tokens

---

## Page Hierarchy

```
[Parent Page]
  └── Microservice Design Patterns (parent hub)
        ├── CQRS -- Command Query Responsibility Segregation
        ├── Transactional Outbox Pattern
        ├── API Gateway Pattern
        └── Pattern Selection Summary
```

---

## Publishing Order

Pages must be created in this order (parent before children):

1. **Microservice Design Patterns** (parent hub page)
2. **CQRS** (child of hub)
3. **Transactional Outbox** (child of hub)
4. **API Gateway** (child of hub)
5. **Pattern Selection Summary** (child of hub)

---

## Step 1: Create the Parent Hub Page

```bash
PARENT_ID=$(curl -s -X POST \
  "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "'"${CONFLUENCE_SPACE_KEY}"'",
    "status": "current",
    "title": "Microservice Design Patterns",
    "parentId": "'"${CONFLUENCE_PARENT_PAGE_ID}"'",
    "body": {
      "representation": "storage",
      "value": "<h1>Microservice Design Patterns</h1><p>Comprehensive documentation for three foundational microservice architecture patterns implemented in Java with Spring Boot 4.0.0.</p><ac:structured-macro ac:name=\"children\" /><h2>Patterns Covered</h2><table><tr><th>Pattern</th><th>Use Case</th><th>Tech Stack</th></tr><tr><td><strong>CQRS</strong></td><td>Separate read and write models for independent scaling and optimization</td><td>Spring Boot, Spring Data JPA, PostgreSQL</td></tr><tr><td><strong>Transactional Outbox</strong></td><td>Reliable event publishing without distributed transactions</td><td>Spring Boot, Spring Data JPA, PostgreSQL, Apache Kafka</td></tr><tr><td><strong>API Gateway</strong></td><td>Unified entry point with authentication, rate limiting, and circuit breaking</td><td>Spring Cloud Gateway, Resilience4j, Redis</td></tr></table><h2>How These Patterns Work Together</h2><p>In a production microservice architecture, these three patterns are complementary. The API Gateway provides a single entry point; CQRS separates read and write models for performance; and the Transactional Outbox guarantees reliable event publishing between services.</p><p><em>Date: 2026-02-28 | Author: Saurabh Sharma</em></p>"
    }
  }' | jq -r '.id')

echo "Created parent page with ID: ${PARENT_ID}"
```

---

## Step 2: Publish CQRS Page

Convert the CQRS documentation (README + scenario + implementation + dependencies) into a single Confluence page.

```bash
# Read all CQRS markdown files and convert to a combined body
# For production use, use a Markdown-to-Confluence converter like:
#   - mark (https://github.com/kovetskiy/mark)
#   - markdown-to-confluence (npm package)

curl -s -X POST \
  "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "'"${CONFLUENCE_SPACE_KEY}"'",
    "status": "current",
    "title": "CQRS -- Command Query Responsibility Segregation",
    "parentId": "'"${PARENT_ID}"'",
    "body": {
      "representation": "storage",
      "value": "<h1>CQRS -- Command Query Responsibility Segregation</h1><p><strong>Separate the data-mutation (command) model from the data-retrieval (query) model so each side can be optimised, scaled, and evolved independently.</strong></p><h2>When to Use This Pattern</h2><ul><li>Read and write workloads have drastically different throughput or latency requirements (e.g., 100:1 read-to-write ratio).</li><li>The read schema needs to be denormalised for fast queries while the write schema must stay normalised for integrity.</li><li>You need an audit trail of every state-changing operation.</li><li>Different teams own the read and write pipelines.</li></ul><h2>Real-World Scenario: E-Commerce Order Management</h2><p>ShopStream processes 12,000 orders per hour at peak. The read-to-write ratio is 100:1. A single shared model forces JOINs on the read path (p99 &gt; 2s) and lock contention on the write path.</p><h2>Architecture</h2><ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">text</ac:parameter><ac:plain-text-body><![CDATA[Customer/Admin -> Command API (POST/PUT) -> Command Handlers -> Write DB (normalised)\n                                                                         |\n                                                                   Domain Events\n                                                                         |\n                                                                    Projector\n                                                                         |\nCustomer/Admin -> Query API (GET) -> Query Handlers -> Read DB (denormalised)]]></ac:plain-text-body></ac:structured-macro><p><em>Full implementation details including Java code, database schemas, and dependency justifications are available in the project repository under /cqrs/.</em></p>"
    }
  }'
```

---

## Step 3: Publish Transactional Outbox Page

```bash
curl -s -X POST \
  "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "'"${CONFLUENCE_SPACE_KEY}"'",
    "status": "current",
    "title": "Transactional Outbox Pattern",
    "parentId": "'"${PARENT_ID}"'",
    "body": {
      "representation": "storage",
      "value": "<h1>Transactional Outbox Pattern</h1><p><strong>Guarantee reliable event publishing by writing the outgoing event into a database table within the same ACID transaction as the business data change, then asynchronously relaying it to the message broker.</strong></p><h2>The Dual-Write Problem</h2><p>Writing to a database and a message broker in separate steps risks data loss. If the broker is down or the service crashes between the two writes, events are permanently lost.</p><h2>Real-World Scenario: Payment Processing at FinFlow</h2><p>FinFlow processes 50,000 payments/day. Downstream services (Ledger, Notifications, Analytics, Fraud Detection) consume events via Kafka. Missing a payment event causes reconciliation errors and regulatory audit failures.</p><h2>Solution</h2><p>Write the event to an outbox_events table in the same transaction as the payment. A scheduled poller reads pending rows and publishes them to Kafka. At-least-once delivery is guaranteed.</p><ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">text</ac:parameter><ac:plain-text-body><![CDATA[Service -> BEGIN TX -> INSERT payments -> INSERT outbox_events -> COMMIT\n                                                                    |\n                                                            OutboxPoller (async)\n                                                                    |\n                                                              Apache Kafka\n                                                                    |\n                                                     Ledger / Notification / Analytics]]></ac:plain-text-body></ac:structured-macro><p><em>Full implementation details including Java code, database schemas, Kafka configuration, and Debezium alternative are available in the project repository under /transactional-outboxing/.</em></p>"
    }
  }'
```

---

## Step 4: Publish API Gateway Page

```bash
curl -s -X POST \
  "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "'"${CONFLUENCE_SPACE_KEY}"'",
    "status": "current",
    "title": "API Gateway Pattern",
    "parentId": "'"${PARENT_ID}"'",
    "body": {
      "representation": "storage",
      "value": "<h1>API Gateway Pattern</h1><p><strong>Provide a single entry point for all client requests, centralising cross-cutting concerns like routing, authentication, rate limiting, and circuit breaking.</strong></p><h2>Real-World Scenario: RetailHub Multi-Service Platform</h2><p>RetailHub has 5 backend microservices (Product, User, Order, Review, Notification). Without a gateway, clients must know 5 hostnames, security is duplicated inconsistently, and there is no protection against cascading failures.</p><h2>Key Features Implemented</h2><ul><li>JWT authentication (global filter)</li><li>Per-user rate limiting (Redis-backed)</li><li>Circuit breaking with fallback responses (Resilience4j)</li><li>Correlation ID propagation for distributed tracing</li><li>CORS configuration</li></ul><ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"language\">text</ac:parameter><ac:plain-text-body><![CDATA[Clients -> API Gateway (:8080)\n              |\n    +---------+---------+\n    |         |         |\n /products  /users   /orders\n    |         |         |\n Product   User     Order\n Service   Service  Service\n (:8081)   (:8082)  (:8083)]]></ac:plain-text-body></ac:structured-macro><p><em>Full implementation details including Spring Cloud Gateway configuration, filter code, and infrastructure setup are available in the project repository under /api-gateway/.</em></p>"
    }
  }'
```

---

## Step 5: Publish Pattern Selection Summary Page

```bash
curl -s -X POST \
  "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "'"${CONFLUENCE_SPACE_KEY}"'",
    "status": "current",
    "title": "Microservice Pattern Selection Guide",
    "parentId": "'"${PARENT_ID}"'",
    "body": {
      "representation": "storage",
      "value": "<h1>Pattern Selection Guide</h1><h2>Business Requirements Matching</h2><table><tr><th>Business Requirement</th><th>Best Pattern</th><th>Why</th></tr><tr><td>High read/write ratio (100:1)</td><td>CQRS</td><td>Separate read model with denormalised schema</td></tr><tr><td>Reliable event publishing without 2PC</td><td>Transactional Outbox</td><td>Same-transaction writes to business table and outbox</td></tr><tr><td>Unified API for multiple services</td><td>API Gateway</td><td>Single entry point with path-based routing</td></tr><tr><td>Per-user rate limiting</td><td>API Gateway</td><td>Redis-backed distributed counters</td></tr><tr><td>Prevent cascading failures</td><td>API Gateway</td><td>Circuit breaker with fallback responses</td></tr><tr><td>Audit trail of every state change</td><td>CQRS + Outbox</td><td>Commands and events form an immutable history</td></tr></table><h2>Recommended Learning Path</h2><ol><li><strong>Phase 1 (Month 1-2):</strong> API Gateway -- immediate value, low risk</li><li><strong>Phase 2 (Month 3-4):</strong> Transactional Outbox -- solves dual-write problem</li><li><strong>Phase 3 (Month 5-6):</strong> CQRS -- highest complexity, highest reward</li></ol><p><em>Full decision flowchart, anti-patterns, and technology stack details are available in the project repository under /docs/summary.md.</em></p>"
    }
  }'
```

---

## Automated Publishing with `mark`

For a more robust approach, use the [`mark`](https://github.com/kovetskiy/mark) tool, which converts Markdown to Confluence directly:

```bash
# Install mark
go install github.com/kovetskiy/mark@latest

# Publish each file with mark
mark -u "${CONFLUENCE_USERNAME}" \
     -p "${CONFLUENCE_API_TOKEN}" \
     -b "${CONFLUENCE_BASE_URL}/wiki" \
     -f cqrs/README.md \
     --space "${CONFLUENCE_SPACE_KEY}" \
     --parents "Microservice Design Patterns"

mark -u "${CONFLUENCE_USERNAME}" \
     -p "${CONFLUENCE_API_TOKEN}" \
     -b "${CONFLUENCE_BASE_URL}/wiki" \
     -f transactional-outboxing/README.md \
     --space "${CONFLUENCE_SPACE_KEY}" \
     --parents "Microservice Design Patterns"

mark -u "${CONFLUENCE_USERNAME}" \
     -p "${CONFLUENCE_API_TOKEN}" \
     -b "${CONFLUENCE_BASE_URL}/wiki" \
     -f api-gateway/README.md \
     --space "${CONFLUENCE_SPACE_KEY}" \
     --parents "Microservice Design Patterns"

mark -u "${CONFLUENCE_USERNAME}" \
     -p "${CONFLUENCE_API_TOKEN}" \
     -b "${CONFLUENCE_BASE_URL}/wiki" \
     -f docs/summary.md \
     --space "${CONFLUENCE_SPACE_KEY}" \
     --parents "Microservice Design Patterns"
```

---

## Verification

After publishing, verify each page:

```bash
# List all pages under the parent
curl -s "${CONFLUENCE_BASE_URL}/wiki/api/v2/pages/${PARENT_ID}/children" \
  -H "Authorization: Basic $(echo -n "${CONFLUENCE_USERNAME}:${CONFLUENCE_API_TOKEN}" | base64)" \
  | jq '.results[] | {id, title, status}'
```

Expected output:
```json
{"id": "...", "title": "CQRS -- Command Query Responsibility Segregation", "status": "current"}
{"id": "...", "title": "Transactional Outbox Pattern", "status": "current"}
{"id": "...", "title": "API Gateway Pattern", "status": "current"}
{"id": "...", "title": "Microservice Pattern Selection Guide", "status": "current"}
```

---

## MCP Server (Alternative)

This project has an Atlassian MCP server configured in `.mcp.json`:

```json
{
  "mcpServers": {
    "atlassian": {
      "type": "sse",
      "url": "https://mcp.atlassian.com/v1/sse"
    }
  }
}
```

If the Atlassian MCP server is authenticated and available, it can be used to create and update Confluence pages programmatically through the Claude Code agent. Ensure OAuth2 credentials are configured for the MCP server endpoint.
