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
This starts PostgreSQL (5432), PgBouncer (6432), Zookeeper (2181), Kafka (9092), Schema Registry (8081), and Pinot (Controller 9000, Broker 8099).

Run services individually via IDE or:
```bash
mvn spring-boot:run -pl analytics-gateway     # Port 8080
mvn spring-boot:run -pl ad-click-service       # Port 8081
mvn spring-boot:run -pl ad-click-processor     # Port 8083
```

## Architecture

```
Analytics Gateway (:8080)  ──routes──►  Ad-Click Service (:8081)
        │                                      │
        │                                      │ publishes Avro events
        └──routes──► Pinot Broker (:8099)      ▼
                          ▲              Kafka + Schema Registry
                          │                    │
                          └──── Ad-Click Processor (:8083, Flink)
                                               │
                                          PostgreSQL (:5432)
```

### Module Responsibilities

- **ads-domain**: Shared Avro schema (`AdClickEvent.avsc`) → generates Java classes at build time. All modules that handle events depend on this.
- **ad-click-service**: Reactive REST API (WebFlux + R2DBC). Persists events to PostgreSQL with idempotent writes (unique `event_id`), publishes to Kafka topic `ad-click-events`. Flyway manages DB migrations.
- **ad-click-processor**: Spring-managed Apache Flink job. Consumes from Kafka, performs windowed aggregations, sinks to Pinot. Uses `@Profile("!test")` to skip job execution during testing.
- **analytics-gateway**: Spring Cloud Gateway. Routes `/api/v1/events/**` to ad-click-service and `/pinot/**` to Pinot broker. Configures global CORS.
- **ad-service**: Legacy/placeholder module, minimal content.

### Key Patterns

- **Reactive everywhere**: WebFlux controllers return `Mono`/`Flux`, R2DBC for non-blocking DB access. Flyway still uses JDBC (separate connection config in `application.yml`).
- **Dual DB config**: R2DBC (`spring.r2dbc.*`) for reactive queries, JDBC (`spring.flyway.*`) for migrations — both pointing at the same PostgreSQL instance.
- **Avro serialization**: Kafka messages use Confluent `KafkaAvroSerializer` with Schema Registry. The Avro schema in `ads-domain` is the single source of truth.
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

- `deploy-azure.sh` for Azure deployment automation
- Kubernetes manifests in `k8s/` (namespace, secrets, ad-click-service deployment)
- Target: ~$250/month Azure budget for 1000 req/sec, 10M events/day
