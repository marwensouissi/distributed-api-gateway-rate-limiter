#!/bin/bash
# =====================================================================
#  COMPREHENSIVE TEST SUITE FOR DISTRIBUTED API GATEWAY
#  Run this script from the project root directory
# =====================================================================

set -e

GATEWAY_URL="http://localhost:8082"
BACKEND_URL="http://localhost:8034"
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "====================================================================="
echo " DISTRIBUTED API GATEWAY - COMPREHENSIVE TEST SUITE"
echo "====================================================================="
echo ""

# =====================================================================
echo "[TEST 1/7] Infrastructure Health Check"
# =====================================================================
echo "Checking Docker containers..."
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "redis|kafka|zookeeper|prometheus|grafana" || echo "Some containers may not be running"
echo ""

# =====================================================================
echo "[TEST 2/7] API Gateway Health Check"
# =====================================================================
echo "Testing API Gateway actuator endpoint..."
HEALTH=$(curl -s $GATEWAY_URL/actuator/health | grep -o '"status":"UP"')
if [ "$HEALTH" == '"status":"UP"' ]; then
    echo -e "${GREEN}✓ Gateway is healthy${NC}"
else
    echo -e "${RED}✗ Gateway health check failed${NC}"
fi
echo ""

# =====================================================================
echo "[TEST 3/7] Prometheus Metrics Endpoint"
# =====================================================================
echo "Testing Prometheus metrics scrape endpoint..."
METRICS=$(curl -s $GATEWAY_URL/actuator/prometheus | head -5)
echo "$METRICS"
echo ""

# =====================================================================
echo "[TEST 4/7] Basic API Request (Through Gateway)"
# =====================================================================
echo "Sending a basic request through the gateway..."
curl -i -X GET $GATEWAY_URL/api/v1/resource -H "Content-Type: application/json"
echo ""
echo ""

# =====================================================================
echo "[TEST 5/7] Rate Limit Test (Burst 110 requests)"
# =====================================================================
echo "Sending 110 rapid requests to trigger rate limiting..."
SUCCESS=0
BLOCKED=0
for i in $(seq 1 110); do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" $GATEWAY_URL/api/v1/resource)
    if [ "$STATUS" == "200" ]; then
        ((SUCCESS++))
    elif [ "$STATUS" == "429" ]; then
        ((BLOCKED++))
    fi
done
echo "Results: $SUCCESS successful, $BLOCKED rate-limited (429)"
echo ""

# =====================================================================
echo "[TEST 6/7] Redis Rate Limit Keys"
# =====================================================================
echo "Checking Redis for rate limit keys..."
docker exec redis redis-cli KEYS "ratelimit:*" 2>/dev/null || echo "Could not connect to Redis"
echo ""

# =====================================================================
echo "[TEST 7/7] Kafka Topics Verification"
# =====================================================================
echo "Listing Kafka topics..."
docker exec kafka1 kafka-topics --bootstrap-server localhost:29092 --list 2>/dev/null || echo "Could not connect to Kafka"
echo ""

echo "====================================================================="
echo " ALL TESTS COMPLETED"
echo "====================================================================="
echo ""
echo "Next Steps:"
echo "  1. Run k6 load test: k6 run testing/load-test.js"
echo "  2. Check Grafana dashboards: http://localhost:3000"
echo "  3. Check Prometheus metrics: http://localhost:9090"
