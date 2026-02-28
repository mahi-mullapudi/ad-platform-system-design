# Reactive Ad Analytics Platform - Quick Start

## Start Infrastructure

```bash
docker-compose up -d
```

## Build & Run

```bash
# Build all modules
mvn clean install -DskipTests

# Run services (in separate terminals)
cd analytics-gateway && mvn spring-boot:run
cd ad-click-service && mvn spring-boot:run  
cd ad-click-processor && mvn spring-boot:run
```

## Test

```bash
# Record a click
curl -X POST http://localhost:8080/api/v1/events/clicks \
  -H "Content-Type: application/json" \
  -d '{
    "adId": "ad-001",
    "campaignId": "campaign-001",
    "userId": "user-123",
    "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
  }'

# Query events
curl "http://localhost:8080/api/v1/events/campaign/campaign-001?start=2024-01-01T00:00:00Z&end=2024-12-31T23:59:59Z"
```

## Services

- Analytics Gateway: http://localhost:8080
- Ad Click Service: http://localhost:8081
- Ad Click Processor: http://localhost:8083
- Pinot Console: http://localhost:9000

## Azure Cost Summary

**Budget: $250/month**
- PostgreSQL Flexible (2 vCores): $80
- AKS (2 nodes, B4ms): $120
- Event Hubs (2 TUs): $25
- Container Registry: $5
- App Gateway: $20
- Monitor: $10
- **Total: ~$260/month**

**Traffic Capacity:**
- 1000 req/sec sustained
- 10M events/day
- 300M events/month

**Lower-cost option: ~$180/month** (use Container Instances instead of AKS)
