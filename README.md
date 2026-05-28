# Order API

A production-style order management REST API built with Clean Architecture principles. Handles order lifecycle from creation through delivery, with an Outbox Pattern implementation for reliable event publishing.

## Stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Language     | Java 21 (records, sealed classes, switch expressions) |
| Framework    | Spring Boot 3.4                     |
| Persistence  | Spring Data JPA + Hibernate 6       |
| Database     | PostgreSQL 16                       |
| Migrations   | Flyway                              |
| Build        | Gradle (Kotlin DSL)                 |
| Containers   | Docker Compose                      |
| Testing      | JUnit 5 + Testcontainers + Awaitility |

## Architecture

Four-layer Clean Architecture. Dependencies point inward — domain has zero framework imports.

```
┌─────────────────────────────────────────────┐
│                  api/                        │
│  controller/  OrderController               │
│  dto/         Request & Response records     │
├─────────────────────────────────────────────┤
│              application/                    │
│  usecase/     CreateOrderUseCase             │
│               UpdateOrderStatusUseCase       │
├─────────────────────────────────────────────┤
│                domain/                       │
│  model/       Order, OrderItem, OrderStatus  │
│               OutboxEvent                    │
│  repository/  OrderRepository (interface)    │
│               OutboxRepository (interface)   │
├─────────────────────────────────────────────┤
│                infra/                        │
│  entity/      JPA entities (OrderEntity,     │
│               OrderItemEntity,               │
│               OutboxEventEntity)             │
│  repository/  JPA implementations            │
│  outbox/      OutboxPublisher (@Scheduled)   │
│  config/      JacksonConfig                  │
└─────────────────────────────────────────────┘
```

**Key boundaries:**
- `domain/model/` — pure Java, no Spring or JPA annotations. `Order` is the Aggregate Root.
- `domain/repository/` — interfaces only. The domain defines *what* it needs, not *how*.
- `infra/entity/` — JPA entities with `fromDomain()` / `toDomain()` mappers. Keeps Hibernate concerns out of the domain.
- `application/usecase/` — orchestrates domain logic and persistence within `@Transactional` boundaries.

## Outbox Pattern

Every state change writes an event to the `outbox_events` table in the **same database transaction** as the order mutation. A `@Scheduled` job polls for `PENDING` events and marks them `PUBLISHED`.

```
┌──────────────────────────────────────────┐
│           @Transactional                 │
│                                          │
│  1. Save/update Order ─────► orders      │
│  2. Save OutboxEvent ──────► outbox      │
│                                          │
│  ── single commit ──                     │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│        OutboxPublisher (every 5s)        │
│                                          │
│  1. SELECT * FROM outbox WHERE PENDING   │
│  2. Publish (log / message broker)       │
│  3. UPDATE status = PUBLISHED            │
└──────────────────────────────────────────┘
```

**Why Outbox instead of publishing directly?**
- No dual-write problem — if the transaction rolls back, the event is never created.
- At-least-once delivery guarantee without distributed transactions.
- Events survive application crashes — they persist in the database until published.

## Endpoints

### POST /orders

Creates a new order. Returns `201 Created` with a `Location` header.

**Request:**
```json
{
  "customerId": "4bab4b41-0927-4389-9c1e-7ac2bc882a5b",
  "items": [
    {
      "productId": "a1111111-1111-1111-1111-111111111111",
      "productName": "Notebook",
      "quantity": 2,
      "unitPrice": 3500.00
    },
    {
      "productId": "b2222222-2222-2222-2222-222222222222",
      "productName": "Mouse",
      "quantity": 1,
      "unitPrice": 120.00
    }
  ]
}
```

**Response:** `201 Created`
```
Location: /orders/e0c92882-8463-42b9-9a75-6c5c4a83d988
```
```json
{
  "id": "e0c92882-8463-42b9-9a75-6c5c4a83d988",
  "customerId": "4bab4b41-0927-4389-9c1e-7ac2bc882a5b",
  "status": "PENDING",
  "totalAmount": 7120.00,
  "items": [
    {
      "id": "8de5d8e9-f7a0-4030-adc8-765314f278b4",
      "productId": "a1111111-1111-1111-1111-111111111111",
      "productName": "Notebook",
      "quantity": 2,
      "unitPrice": 3500.00,
      "subtotal": 7000.00
    },
    {
      "id": "cc74d65d-bd97-4380-b44d-03f493e61b55",
      "productId": "b2222222-2222-2222-2222-222222222222",
      "productName": "Mouse",
      "quantity": 1,
      "unitPrice": 120.00,
      "subtotal": 120.00
    }
  ],
  "createdAt": "2026-05-26T01:36:08.065234Z",
  "updatedAt": "2026-05-26T01:36:08.065234Z"
}
```

### GET /orders/{id}

Returns a single order by ID.

**Response:** `200 OK` (same shape as above) or `404 Not Found`.

### GET /orders?page=0&size=10&status=PENDING

Lists orders with pagination and optional status filter.

| Parameter | Required | Default | Description          |
|-----------|----------|---------|----------------------|
| `page`    | No       | `0`     | Zero-based page index |
| `size`    | No       | `10`    | Page size             |
| `status`  | No       | —       | Filter by OrderStatus |

**Response:** `200 OK`
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0,
  "size": 10,
  "numberOfElements": 10,
  "first": true,
  "last": false,
  "empty": false
}
```

### PATCH /orders/{id}/status

Transitions the order to a new status. Validates against the state machine.

**Request:**
```json
{
  "status": "PAID"
}
```

**Response:** `200 OK` (full order) or `422 Unprocessable Entity` with RFC 7807 Problem Detail:
```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Order e0c928... cannot be cancelled in status SHIPPED",
  "instance": "/orders/e0c928.../status"
}
```

## Order State Machine

```
                    ┌───────────────┐
                    │    PENDING    │
                    └──────┬────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
     ┌────────────┐  ┌──────────┐  ┌───────────┐
     │    PAID    │  │ PAYMENT  │  │ CANCELLED │
     └─────┬──────┘  │ _FAILED  │  └───────────┘
           │         └──────────┘    ▲  ▲  ▲
     ┌─────┼──────────┐              │  │  │
     ▼     ▼          │              │  │  │
┌─────────┐ ┌────────┐│   ┌─────────┘  │  │
│PROCESSIN│ │ OUT_OF ││   │            │  │
│   G     │ │ _STOCK ├┼───┘            │  │
└────┬────┘ └───┬────┘│                │  │
     │          │     └────────────────┘  │
     │          └──► PROCESSING ──────────┘
     ▼                   │
┌──────────┐             │
│ SHIPPED  │◄────────────┘
└────┬─────┘
     ▼
┌──────────┐
│DELIVERED │
└──────────┘
```

**Transitions:**

| From          | Allowed targets                          |
|---------------|------------------------------------------|
| PENDING       | PAID, PAYMENT_FAILED, CANCELLED          |
| PAID          | PROCESSING, OUT_OF_STOCK, CANCELLED      |
| OUT_OF_STOCK  | PROCESSING, CANCELLED                    |
| PROCESSING    | SHIPPED                                  |
| SHIPPED       | DELIVERED                                |
| DELIVERED     | *(terminal)*                             |
| PAYMENT_FAILED| *(terminal)*                             |
| CANCELLED     | *(terminal)*                             |

Implemented as a `switch` expression in `OrderStatus.canTransitionTo()`.

## Business Rules

**1. Total amount is computed, not accepted from the client.**
`Order.calculateTotal()` sums `quantity * unitPrice` for each item. The client cannot override it.

**2. Historical pricing on OrderItem.**
Each `OrderItem` stores `unitPrice` at the time of order creation. If the product price changes later, existing orders are unaffected.

**3. Cancellation is only allowed before SHIPPED.**
`OrderStatus.isCancellable()` returns `false` for SHIPPED, DELIVERED, and all terminal states. Attempting to cancel a shipped order returns `422`.

**4. Atomic outbox on every state change.**
Both `CreateOrderUseCase` and `UpdateOrderStatusUseCase` run inside `@Transactional`. The order mutation and the outbox event are committed together or not at all.

## Running Locally

**Prerequisites:** Docker, Java 21.

```bash
# Start PostgreSQL
docker compose up -d

# Run the application
./gradlew bootRun
```

The API starts on `http://localhost:8082`.

```bash
# Quick smoke test
curl -s http://localhost:8082/orders?page=0\&size=1 | jq
```

## Running Tests

Tests use Testcontainers — Docker must be running, but no manual database setup is needed.

```bash
./gradlew test
```

5 integration tests covering:
1. Order creation with atomic outbox event
2. Status transition with atomic outbox event
3. Rejection of invalid cancellation (SHIPPED)
4. Pagination and status filtering
5. OutboxPublisher processing pending events

## Technical Decisions

**Why Outbox Pattern?**
Publishing events directly from application code creates a dual-write problem: the database commit succeeds but the message broker publish fails (or vice versa). The outbox table guarantees consistency — the event lives in the same transaction as the data change. The publisher job provides at-least-once delivery with no distributed transaction coordinator.

**Why 201 with Location header?**
`POST /orders` is a synchronous resource creation. `201 Created` with a `Location` header follows RFC 7231 and lets clients discover the new resource URL without parsing the body. API clients and intermediaries (proxies, CDNs) understand this contract natively.

**Why historical pricing on OrderItem?**
Product prices change. If `OrderItem` only stored a `productId`, calculating the order total would require a join to the product catalog — and the total would silently change when prices are updated. Storing `unitPrice` at creation time makes the order a self-contained snapshot.

**Why mandatory pagination?**
The `GET /orders` endpoint always returns a `Page`. Unbounded `SELECT *` on a growing orders table is a production incident waiting to happen. Pagination defaults (`page=0, size=10`) keep responses predictable and protect the database from full-table scans.

**Why Testcontainers?**
H2 and embedded databases diverge from PostgreSQL in subtle ways: enum handling, JSON operators, index behavior, constraint enforcement. Testcontainers runs the exact same PostgreSQL 16 image used in production, catching integration bugs that in-memory databases miss.

## Next Steps

- **Kafka integration** — Replace the `@Scheduled` outbox publisher with a Kafka Connect CDC connector (Debezium) or a Kafka producer, delivering events to downstream consumers in near real-time.
- **Saga Pattern** — Orchestrate cross-service workflows with `payment-api` and `notification-api`. Order creation triggers a payment saga; payment confirmation triggers shipping and customer notification.
- **Observability** — Add structured logging, distributed tracing (OpenTelemetry), and metrics export to Datadog or Prometheus/Grafana. Track order creation rate, status transition latency, and outbox publish lag.
- **Rate limiting** — Protect the API with per-client rate limits (Bucket4j or Spring Cloud Gateway) to prevent abuse on write endpoints.
