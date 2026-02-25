# Reactive Ad Analytics Platform

A Netflix-style reactive ad analytics platform built with Spring Boot, R2DBC PostgreSQL, Apache Flink, Apache Kafka, and Apache Pinot for real-time click/impression tracking and analytics.

## Architecture Overview

```
┌─────────────────┐
│ Analytics       │ :8080 (API Gateway)
│ Gateway         │
└────────┬────────┘
         │
    ┌────┴────────────────────────┐
    │                             │
┌───▼──────────┐         ┌────────▼────────┐
│ Ad-Click     │         │ Apache Pinot    │
│ Service      │ :8081   │ Broker :8099    │
│ (R2DBC+PG)   │         │ (Analytics)     │
└──────┬───────┘         └─────────────────┘
       │                          ▲
       │ Kafka Events             │
       ▼                          │
┌──────────────┐         ┌────────┴────────┐
│ Kafka +      │         │ Ad-Click        │
│ Schema       │ :9092   │ Processor       │ :8083
│ Registry     │ :8081   │ (Flink Stream)  │
└──────────────┘         └─────────────────┘
       │
       ▼
┌──────────────┐
│ PostgreSQL + │ :5432
│ PgBouncer    │ :6432
└──────────────┘
```

## System Components

### 1. **ad-click-service** (Port 8081)
- Reactive REST API for recording ad clicks/impressions
- **R2DBC PostgreSQL** for reactive persistence
- **Flyway** for schema migrations (JDBC)
- **Table partitioning** by timestamp for efficient time-series queries
- **BRIN indexes** for fast range scans
- **JSONB columns** for flexible metadata storage
- **Idempotent writes** via unique `event_id` constraint
- **Kafka publisher** for downstream stream processing

### 2. **ad-click-processor** (Port 8083)
- **Spring-managed Apache Flink** streaming job
- Reads from Kafka with Avro deserialization
- Performs windowed aggregations (clicks/impressions per campaign)
- Sinks to Apache Pinot for real-time analytics
- Lifecycle controlled by Spring Boot (start/stop hooks)
- Configuration via Spring properties

### 3. **analytics-gateway** (Port 8080)
- Spring Cloud Gateway for unified API access
- Routes to ad-click-service and Pinot broker
- CORS enabled for frontend integration
- Actuator endpoints for monitoring

### 4. **ads-domain**
- Shared Avro schemas for event serialization
- Domain models used across services

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Persistence** | PostgreSQL 16 + R2DBC | Reactive click/impression storage with partitioning |
| **Connection Pool** | PgBouncer | Transaction pooling (max 1000 clients, pool size 50) |
| **Stream Processing** | Apache Flink 1.18 | Windowed aggregations, real-time analytics |
| **Message Bus** | Apache Kafka 7.5 | Event streaming backbone |
| **Schema Registry** | Confluent Schema Registry | Avro schema versioning |
| **Analytics DB** | Apache Pinot 1.0 | Real-time OLAP queries |
| **API Gateway** | Spring Cloud Gateway | Routing, CORS, rate limiting |
| **Serialization** | Apache Avro 1.11.3 | Schema evolution, compact binary format |
| **Migration** | Flyway | Database schema versioning |

## Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.8+

### 1. Start Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d

# Verify services are healthy
docker-compose ps
```

Services started:
- PostgreSQL: localhost:5432
- PgBouncer: localhost:6432
- Kafka: localhost:9092
- Schema Registry: localhost:8081
- Pinot Controller: localhost:9000
- Pinot Broker: localhost:8099
- Zookeeper: localhost:2181

### 2. Build the Project

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests
```

### 3. Run Services

```bash
# Terminal 1: Analytics Gateway
cd analytics-gateway
mvn spring-boot:run

# Terminal 2: Ad Click Service
cd ad-click-service
mvn spring-boot:run

# Terminal 3: Ad Click Processor (Flink)
cd ad-click-processor
mvn spring-boot:run
```

### 4. Test the API

```bash
# Record a click event
curl -X POST http://localhost:8080/api/v1/events/clicks \
  -H "Content-Type: application/json" \
  -d '{
    "adId": "ad-12345",
    "campaignId": "campaign-abc",
    "userId": "user-789",
    "timestamp": "2024-01-15T10:30:00Z",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0...",
    "metadata": "{\"platform\": \"web\", \"device\": \"desktop\"}"
  }'

# Record an impression
curl -X POST http://localhost:8080/api/v1/events/impressions \
  -H "Content-Type: application/json" \
  -d '{
    "adId": "ad-12345",
    "campaignId": "campaign-abc",
    "userId": "user-789",
    "timestamp": "2024-01-15T10:29:00Z"
  }'

# Query events by campaign
curl "http://localhost:8080/api/v1/events/campaign/campaign-abc?start=2024-01-15T00:00:00Z&end=2024-01-15T23:59:59Z"

# Count clicks for a campaign
curl "http://localhost:8080/api/v1/events/campaign/campaign-abc/count?eventType=CLICK&startTime=2024-01-15T00:00:00Z"
```

## Database Schema

### ad_click_events Table

```sql
CREATE TABLE ad_click_events (
    id BIGSERIAL,
    event_id UUID NOT NULL,           -- Unique for idempotency
    ad_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,  -- CLICK or IMPRESSION
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    metadata JSONB,                   -- Flexible JSONB for custom attributes
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, campaign_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Indexes
CREATE UNIQUE INDEX idx_event_id ON ad_click_events (event_id);
CREATE INDEX idx_campaign_timestamp ON ad_click_events USING BRIN (campaign_id, timestamp);
CREATE INDEX idx_ad_timestamp ON ad_click_events USING BRIN (ad_id, timestamp);
CREATE INDEX idx_metadata_gin ON ad_click_events USING GIN (metadata);
```

**Partitioning Strategy:**
- Daily partitions by `timestamp`
- Auto-creation trigger for future partitions
- BRIN indexes for efficient time-series scans
- 37 initial partitions (past 30 days + next 7 days)

## Configuration

### Environment Variables

```bash
# PostgreSQL
export POSTGRES_URL=jdbc:postgresql://localhost:5432/ads_platform
export POSTGRES_USER=ads_user
export POSTGRES_PASSWORD=ads_password

# Kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export SCHEMA_REGISTRY_URL=http://localhost:8081

# Pinot
export PINOT_BROKER_URL=localhost:8099
```

### Production Profiles

Create `application-prod.yml` in each service:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/ads_platform
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    pool:
      max-size: 100
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
```

## Monitoring & Observability

### Health Checks
- Gateway: http://localhost:8080/actuator/health
- Ad-Click Service: http://localhost:8081/actuator/health
- Processor: http://localhost:8083/actuator/health

### Metrics (Prometheus)
- Gateway: http://localhost:8080/actuator/prometheus
- Ad-Click Service: http://localhost:8081/actuator/prometheus
- Processor: http://localhost:8083/actuator/prometheus

## Azure Deployment & Cost Analysis

### Recommended Azure Services

| Service | SKU | Purpose | Monthly Cost (USD) |
|---------|-----|---------|-------------------|
| **Azure Database for PostgreSQL** | Flexible Server, 2 vCores, 8GB RAM, 128GB storage | Click/impression persistence | ~$80 |
| **Azure Kubernetes Service (AKS)** | 2x Standard_B4ms nodes (4 vCores, 16GB each) | Spring Boot services + Flink | ~$120 |
| **Azure Event Hubs** | Standard tier, 2 throughput units | Kafka replacement | ~$25 |
| **Azure Container Registry** | Basic | Docker images | ~$5 |
| **Azure Application Gateway** | Basic tier | API Gateway/Load balancer | ~$20 |
| **Azure Monitor** | Basic logging + metrics | Observability | ~$10 |
| **Total** | | | **~$260/month** |

### Alternative Lower-Cost Setup (~$180/month)

| Service | SKU | Monthly Cost (USD) |
|---------|-----|-------------------|
| **Azure Database for PostgreSQL** | Flexible Server, 1 vCore, 4GB RAM | ~$40 |
| **Azure Container Instances** | 3 containers (2 vCPU, 4GB each) | ~$90 |
| **Azure Event Hubs** | Basic tier, 1 throughput unit | ~$12 |
| **Azure Storage** | Standard LRS, 100GB | ~$2 |
| **Azure Application Gateway** | Basic (or use nginx on Container Instances) | ~$20 |
| **Azure Monitor** | Essential only | ~$5 |
| **Total** | | **~$169/month** |

### Deployment Architecture for Azure

```yaml
# Kubernetes deployment example (AKS)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ad-click-service
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: ad-click-service
        image: <your-acr>.azurecr.io/ad-click-service:latest
        env:
        - name: SPRING_R2DBC_URL
          value: r2dbc:postgresql://<pg-server>.postgres.database.azure.com:5432/ads_platform
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          value: <eventhub-namespace>.servicebus.windows.net:9093
```

### Cost Optimization Tips

1. **Use Azure Event Hubs instead of self-managed Kafka** - Saves ~$50-100/month
2. **Use Azure Database for PostgreSQL Flexible Server** with burstable tier for dev/test
3. **Enable autoscaling** on AKS for dynamic workload handling
4. **Use Azure Container Instances** for non-critical services (cheaper than AKS for low traffic)
5. **Implement connection pooling** (PgBouncer) to reduce database connections
6. **Use Azure Blob Storage** for Flink checkpoints instead of persistent disks

### Traffic Estimates for $250 Budget

With the recommended setup, you can handle:
- **~1000 requests/second** sustained
- **~10M events/day** click tracking
- **~300M events/month**
- **~10GB database growth/month**
- **PostgreSQL**: 2 vCores can handle ~5000 connections via PgBouncer
- **Event Hubs**: 2 TUs = 2MB/sec ingress, 4MB/sec egress
- **AKS**: 2 nodes with 4 vCores each can run all Spring Boot services + Flink

### Scaling Beyond $250/month

If you exceed traffic limits:
1. **Scale PostgreSQL** to 4 vCores (~$160/month)
2. **Add AKS nodes** ($60/month per Standard_B4ms node)
3. **Increase Event Hubs** throughput units ($12.50 per TU/month)
4. **Add read replicas** for PostgreSQL (~$80/month per replica)

**Estimated cost for 10x traffic**: ~$600-800/month

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests with Testcontainers
```bash
mvn verify
```

Tests automatically spin up:
- PostgreSQL container
- Kafka container
- Schema Registry container

## Performance Considerations

### R2DBC + PostgreSQL
- **Connection pooling**: PgBouncer in transaction mode (50 pool size)
- **Short transactions**: Keep R2DBC transactions brief
- **Optimistic locking**: Use version fields where needed
- **Batch operations**: Use `.bufferTimeout()` for batch inserts

### Flink Stream Processing
- **Parallelism**: Configured via Spring properties (default 4)
- **Checkpointing**: Every 60 seconds to prevent data loss
- **Windowing**: Tumbling windows for aggregations
- **Backpressure handling**: Flink automatically applies backpressure

### Kafka
- **Idempotent producer**: Enabled for exactly-once semantics
- **Acks=all**: Wait for all replicas before considering write successful
- **Retries**: 3 retries with exponential backoff

## Next Steps

1. ✅ Add PostgreSQL dependencies (R2DBC + Flyway)
2. ✅ Create baseline migration scripts with partitioning
3. ✅ Scaffold Spring-managed Flink job
4. 🔲 Wire Schema Registry + Avro serializers
5. 🔲 Implement Pinot sink for Flink
6. 🔲 Add integration tests with Testcontainers
7. 🔲 Set up CI/CD pipeline
8. 🔲 Deploy to Azure

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License.

## Support

For questions or issues, please open a GitHub issue or contact the maintainers.
