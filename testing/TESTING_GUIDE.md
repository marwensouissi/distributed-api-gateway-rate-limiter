# API Gateway Testing Guide

This document provides all commands and scripts needed to test the Distributed API Gateway.

## Prerequisites

- Docker containers running (Redis, Kafka, Prometheus, Grafana)
- API Gateway running on port 8082
- k6 installed for load testing (optional)

---

## Quick Start

Run the comprehensive test suite:

```powershell
# Windows
.\testing\run_all_tests.bat

# Linux/Mac
chmod +x testing/run_all_tests.sh && ./testing/run_all_tests.sh
```

---

## Individual Test Commands

### 1. Health Checks

```powershell
# API Gateway health
curl http://localhost:8082/actuator/health

# Redis health
docker exec redis redis-cli PING

# Kafka broker list
docker exec kafka1 kafka-topics --bootstrap-server localhost:29092 --list
```

### 2. Basic API Request

```powershell
# Simple GET request
curl -i http://localhost:8082/api/v1/resource

# With Authorization header
curl -i -H "Authorization: Bearer test-token" http://localhost:8082/api/v1/resource

# With API Key
curl -i -H "X-API-KEY: secret-key" http://localhost:8082/api/v1/resource
```

### 3. Rate Limiting Test

```powershell
# PowerShell: Send 110 requests to trigger rate limiting
1..110 | ForEach-Object { 
    $status = (Invoke-WebRequest -Uri "http://localhost:8082/api/v1/resource" -Method GET -UseBasicParsing).StatusCode
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

# Check consumer groups
docker exec kafka1 kafka-consumer-groups --bootstrap-server localhost:29092 --list
```

### 6. Prometheus Metrics

```powershell
# View all metrics
curl http://localhost:8082/actuator/prometheus

# Filter specific metrics
curl -s http://localhost:8082/actuator/prometheus | findstr "http_server_requests"
```

### 7. Load Testing with k6

```powershell
# Install k6 (if not installed)
winget install k6

# Run load test
k6 run testing/load-test.js

# Run security test
k6 run testing/security-test.js
```

---

## Test Scenarios

| Scenario | Command | Expected Result |
|----------|---------|-----------------|
| Normal Request | `curl http://localhost:8082/api/v1/resource` | `200 OK` |
| Rate Limited | Send 101+ requests/minute | `429 Too Many Requests` |
| With JWT | `curl -H "Authorization: Bearer token" ...` | `200 OK` (logged) |
| With API Key | `curl -H "X-API-KEY: key" ...` | `200 OK` (logged) |
| Invalid Endpoint | `curl http://localhost:8082/invalid` | `404 Not Found` |
| Metrics | `curl .../actuator/prometheus` | Prometheus format data |
| Health | `curl .../actuator/health` | `{"status":"UP"}` |

---

## Monitoring URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| API Gateway | http://localhost:8082 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |
| Redis | localhost:6379 | - |
| Kafka | localhost:9092 | - |
