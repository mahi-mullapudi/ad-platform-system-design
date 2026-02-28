# Reactive Ad Analytics Platform

A production-grade, multi-module Spring Boot microservices system for real-time ad click and impression tracking. Built with reactive patterns, event-driven architecture, and cloud-native deployment on Azure.

## Architecture

```text
┌─────────────────────┐         ┌─────────────────────────┐
│  Analytics Gateway  │─routes─▶│    Ad-Click Service     │
│      (:8080)        │         │       (:8081)           │
│  Spring Cloud GW    │         │  WebFlux + R2DBC + PG   │
└─────────────────────┘         └──────────┬──────────────┘
                                           │ publishes JSON events
                                           ▼
                                    ┌─────────────┐
                                    │    Kafka     │
                                    │   (9092)     │
                                    └──────┬──────┘
                                           │
                                ┌──────────▼──────────┐
                                │  Ad-Click Processor  │
                                │     (:8083, Flink)   │
                                │  Windowed aggregation │
                                └──────────┬──────────┘
                                           │
                                    ┌──────▼──────┐
                                    │ PostgreSQL  │
                                    │   (:5432)   │
                                    └─────────────┘
```

## Tech Stack

| Component | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.2, Spring WebFlux |
| Database | PostgreSQL 16 (R2DBC + Flyway) |
| Messaging | Apache Kafka (Azure Event Hubs in prod) |
| Stream Processing | Apache Flink 1.18 (embedded) |
| API Gateway | Spring Cloud Gateway |
| Build | Maven multi-module |
| Infrastructure | Terraform → Azure (AKS, ACR, Event Hubs, Key Vault) |
| CI/CD | GitHub Actions with OIDC authentication |

## Modules

| Module | Description |
| --- | --- |
| `ads-domain` | Avro schema — canonical event contract definition |
| `ad-click-service` | Reactive REST API for event ingestion and queries |
| `ad-click-processor` | Flink stream processor for windowed aggregations |
| `analytics-gateway` | Spring Cloud Gateway routing and CORS |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

Starts PostgreSQL (5432), Zookeeper (2181), and Kafka (9092).

### 2. Build

```bash
./mvnw clean install -DskipTests
```

### 3. Run Services

In separate terminals:

```bash
./mvnw spring-boot:run -pl analytics-gateway      # Port 8080
./mvnw spring-boot:run -pl ad-click-service        # Port 8081
./mvnw spring-boot:run -pl ad-click-processor      # Port 8083
```

### 4. Test

```bash
# Record a click event
curl -X POST http://localhost:8080/api/v1/events/clicks \
  -H "Content-Type: application/json" \
  -d '{
    "adId": "ad-001",
    "campaignId": "campaign-001",
    "userId": "user-123",
    "timestamp": "2025-01-15T10:00:00Z"
  }'

# Record an impression
curl -X POST http://localhost:8080/api/v1/events/impressions \
  -H "Content-Type: application/json" \
  -d '{
    "adId": "ad-001",
    "campaignId": "campaign-001",
    "userId": "user-456",
    "timestamp": "2025-01-15T10:01:00Z"
  }'

# Query events by campaign
curl "http://localhost:8080/api/v1/events/campaign/campaign-001?start=2025-01-01T00:00:00Z&end=2025-12-31T23:59:59Z"

# Count events by type
curl "http://localhost:8080/api/v1/events/campaign/campaign-001/count?eventType=CLICK&start=2025-01-01T00:00:00Z&end=2025-12-31T23:59:59Z"
```

## API Endpoints

All endpoints are accessed through the gateway at `:8080`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/events/clicks` | Record a click event |
| `POST` | `/api/v1/events/impressions` | Record an impression event |
| `GET` | `/api/v1/events/campaign/{id}` | Query events by campaign + time range |
| `GET` | `/api/v1/events/ad/{id}` | Query events by ad + time range |
| `GET` | `/api/v1/events/campaign/{id}/count` | Count events by type for a campaign |

## Key Design Decisions

- **Reactive everywhere** — WebFlux controllers return `Mono`/`Flux`, R2DBC for non-blocking database access
- **Dual DB config** — R2DBC for reactive queries + JDBC for Flyway migrations (same PostgreSQL instance)
- **JSON serialization** — Kafka messages use `JsonSerializer`; the Avro schema in `ads-domain` serves as the canonical event contract
- **Partitioned storage** — `ad_click_events` table uses PostgreSQL range partitioning by timestamp (daily) with auto-partition creation and BRIN indexes
- **Idempotent writes** — Unique constraint on `event_id` prevents duplicate event processing
- **Flink-in-Spring** — Flink `StreamExecutionEnvironment` as a Spring bean, job runs via `CommandLineRunner` with async execution

## Azure Deployment

Full deployment guide: [`infra/DEPLOY.md`](infra/DEPLOY.md)

**Infrastructure** (Terraform):

- AKS cluster with Workload Identity
- Azure Event Hubs (Kafka-compatible)
- PostgreSQL Flexible Server (VNet-integrated)
- Azure Key Vault with CSI driver
- Azure Container Registry

**Target capacity**: 1,000 req/sec sustained, 10M events/day (~$250/month)

**CI/CD**: GitHub Actions pipeline runs tests → builds Docker images → pushes to ACR → deploys to AKS via OIDC (no stored credentials).

## Project Structure

```text
├── ads-domain/              # Avro schema (event contract)
├── ad-click-service/        # REST API + Kafka producer
│   └── src/main/resources/
│       └── db/migration/    # Flyway SQL migrations
├── ad-click-processor/      # Flink stream processing job
├── analytics-gateway/       # Spring Cloud Gateway
├── infra/
│   └── terraform/           # Azure IaC (AKS, Event Hubs, PG, KV)
├── k8s/                     # Kubernetes manifests
├── .github/workflows/       # CI/CD pipeline
└── docker-compose.yml       # Local development infrastructure
```

## Testing

```bash
# Unit tests
./mvnw test

# Unit + integration tests (requires Docker for Testcontainers)
./mvnw verify

# Single module
./mvnw test -pl ad-click-service

# Single test class
./mvnw test -pl ad-click-service -Dtest=AdClickEventServiceTest
```

Integration tests use [Testcontainers](https://www.testcontainers.org/) to spin up PostgreSQL and Kafka automatically.

## License

MIT
