# API Gateway Testing Guide

This document provides all commands and scripts needed to test the Distributed API Gateway.

## Prerequisites

-   Docker and Docker Compose installed and running.
-   Java 17 installed.
-   k6 installed for load testing (optional).

---

## Quick Start

### 1. Start Infrastructure
Start Redis, Kafka, Zookeeper, Prometheus, and Grafana:
```powershell
docker-compose up -d
```

### 2. Start Services
Open separate terminals for each service:

**Terminal 1: API Gateway** (Port 8033)
```powershell
cd api-gateway
./mvnw spring-boot:run
```

**Terminal 2: Backend Service** (Port 8034)
```powershell
cd backend-service
./mvnw spring-boot:run
```

**Terminal 3: Consumers (Optional)**
```powershell
cd security-analytics
./mvnw spring-boot:run
```

---

## Individual Test Commands

### 1. Health Checks

```powershell
# API Gateway health
curl http://localhost:8033/actuator/health

# Backend health
curl http://localhost:8034/actuator/health

# Redis health
docker exec redis redis-cli PING
```

### 2. Basic API Request

The Gateway listens on port **8033** and forwards `/api/**` requests to the Backend on port **8034**.

```powershell
# Simple GET request
curl -i http://localhost:8033/api/v1/resource

# With Authorization header
curl -i -H "Authorization: Bearer test-token" http://localhost:8033/api/v1/resource

# With API Key
curl -i -H "X-API-KEY: secret-key" http://localhost:8033/api/v1/resource
```

### 3. Rate Limiting Test

```powershell
# PowerShell: Send 110 requests to trigger rate limiting
1..110 | ForEach-Object { 
    $status = (Invoke-WebRequest -Uri "http://localhost:8033/api/v1/resource" -Method GET -UseBasicParsing).StatusCode
    Write-Host "Request $_`: HTTP $status"
}
```

**Expected Result**: First ~100 requests return `200 OK`, remaining return `429 Too Many Requests`.

### 4. Check Rate Limit Keys in Redis

```powershell
# View all rate limit keys
docker exec redis redis-cli KEYS "ratelimit:*"

# View specific key TTL
docker exec redis redis-cli TTL "ratelimit:ip:127.0.0.1"

# Clear all rate limits (for testing reset)
docker exec redis redis-cli FLUSHALL
```

### 5. Kafka Event Verification

```powershell
# List topics
docker exec kafka1 kafka-topics --bootstrap-server localhost:29092 --list

# Consume api-requests topic (real-time view)
docker exec kafka1 kafka-console-consumer --bootstrap-server localhost:29092 --topic api-requests --from-beginning --max-messages 5
```

### 6. Prometheus Metrics

```powershell
# View all metrics
curl http://localhost:8033/actuator/prometheus
```

### 7. Load Testing with k6

```powershell
# Install k6 (if not installed)
winget install k6

# Run load test
k6 run testing/load-test.js
```

---

## Test Scenarios

| Scenario | Command | Expected Result |
|----------|---------|-----------------|
| Normal Request | `curl http://localhost:8033/api/v1/resource` | `200 OK` |
| Rate Limited | Send 101+ requests/minute | `429 Too Many Requests` |
| With JWT | `curl -H "Authorization: Bearer token" ...` | `200 OK` (logged) |
| With Request ID | *Auto-generated internally* | Logged in Kafka |
| Service Down | Backend (8034) stopped | `503 Service Unavailable` |
| Metrics | `curl .../actuator/prometheus` | Prometheus format data |

---

## Monitoring URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| API Gateway | http://localhost:8033 | - |
| Backend Service | http://localhost:8034 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |
| Redis | localhost:6379 | - |
| Kafka | localhost:9092 | - |
