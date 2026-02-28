# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Reactive Ad Analytics Platform — a multi-module Spring Boot microservices system for real-time ad click/impression tracking and analytics. Java 21, Maven multi-module build.

## Build Commands

```bash
# Build all modules (skip tests)
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Run unit tests only
mvn test

# Run unit + integration tests
mvn verify

# Build a single module
mvn clean install -pl ad-click-service -am

# Run a single test class
mvn test -pl ad-click-service -Dtest=AdClickEventServiceTest

# Generate Avro classes from schemas
mvn generate-sources -pl ads-domain
```

## Local Development

Start infrastructure before running services:
```bash
docker-compose up -d
```
This starts PostgreSQL (5432), Zookeeper (2181), and Kafka (9092).

Run services individually via IDE or:
```bash
mvn spring-boot:run -pl analytics-gateway     # Port 8080
mvn spring-boot:run -pl ad-click-service       # Port 8081
mvn spring-boot:run -pl ad-click-processor     # Port 8083
```

## Architecture

```
Analytics Gateway (:8080)  ──routes──►  Ad-Click Service (:8081)
                                               │
                                               │ publishes JSON events
                                               ▼
                                         Kafka (9092)
                                               │
                                   Ad-Click Processor (:8083, Flink)
                                               │
                                         PostgreSQL (:5432)
```

### Module Responsibilities

- **ads-domain**: Shared Avro schema (`AdClickEvent.avsc`) — canonical event contract definition. Generates Java classes at build time.
- **ad-click-service**: Reactive REST API (WebFlux + R2DBC). Persists events to PostgreSQL with idempotent writes (unique `event_id`), publishes to Kafka topic `ad-click-events`. Flyway manages DB migrations.
- **ad-click-processor**: Spring-managed Apache Flink job. Consumes from Kafka, performs windowed aggregations, writes results back. Uses `@Profile("!test")` to skip job execution during testing.
- **analytics-gateway**: Spring Cloud Gateway. Routes `/api/v1/events/**` to ad-click-service. Configures global CORS.

### Key Patterns

- **Reactive everywhere**: WebFlux controllers return `Mono`/`Flux`, R2DBC for non-blocking DB access. Flyway still uses JDBC (separate connection config in `application.yml`).
- **Dual DB config**: R2DBC (`spring.r2dbc.*`) for reactive queries, JDBC (`spring.flyway.*`) for migrations — both pointing at the same PostgreSQL instance.
- **JSON serialization**: Kafka messages use Spring `JsonSerializer` with `StringSerializer` keys. The Avro schema in `ads-domain` serves as the canonical event contract.
- **PostgreSQL partitioning**: `ad_click_events` table uses range partitioning by timestamp (daily). A trigger auto-creates partitions. BRIN indexes for time-series queries.
- **Flink-in-Spring**: Flink `StreamExecutionEnvironment` is a Spring bean, job runs via `CommandLineRunner` with async execution.

### REST API Endpoints (ad-click-service)

- `POST /api/v1/events/clicks` — record click event
- `POST /api/v1/events/impressions` — record impression event
- `GET /api/v1/events/campaign/{campaignId}` — query by campaign + time range
- `GET /api/v1/events/ad/{adId}` — query by ad + time range
- `GET /api/v1/events/campaign/{campaignId}/count` — count events by type

## Database

PostgreSQL 16 with table `ad_click_events`. Migrations in `ad-click-service/src/main/resources/db/migration/`. Default credentials: `ads_user`/`ads_password`, database `ads_platform`.

## Testing

- Testcontainers for integration tests (PostgreSQL, Kafka containers spin up automatically)
- No custom linting or formatting configuration — uses defaults
- Test dependencies: Spring Boot Test, Reactor Test, JUnit Jupiter

## Deployment

- Terraform IaC in `infra/terraform/` (AKS, ACR, Event Hubs, PostgreSQL, Key Vault)
- Kubernetes manifests in `k8s/`
- GitHub Actions CI/CD in `.github/workflows/deploy.yml`
- Deployment guide in `infra/DEPLOY.md`
- Target: ~$250/month Azure budget for 1000 req/sec, 10M events/day
