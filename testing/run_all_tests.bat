@echo off
REM =====================================================================
REM  COMPREHENSIVE TEST SUITE FOR DISTRIBUTED API GATEWAY
REM  Run this script from the project root directory
REM =====================================================================

echo =====================================================================
echo  DISTRIBUTED API GATEWAY - COMPREHENSIVE TEST SUITE
echo =====================================================================
echo.

set GATEWAY_URL=http://localhost:8082
set BACKEND_URL=http://localhost:8081

REM =====================================================================
echo [TEST 1/7] Infrastructure Health Check
REM =====================================================================
echo Checking Docker containers...
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | findstr /i "redis kafka zookeeper prometheus grafana"
echo.

REM =====================================================================
echo [TEST 2/7] API Gateway Health Check
REM =====================================================================
echo Testing API Gateway actuator endpoint...
curl -s %GATEWAY_URL%/actuator/health
echo.
echo.

REM =====================================================================
echo [TEST 3/7] Prometheus Metrics Endpoint
REM =====================================================================
echo Testing Prometheus metrics scrape endpoint...
curl -s %GATEWAY_URL%/actuator/prometheus | findstr /c:"http_server_requests"
echo.
echo.

REM =====================================================================
echo [TEST 4/7] Basic API Request (Through Gateway)
REM =====================================================================
echo Sending a basic request through the gateway...
curl -i -X GET %GATEWAY_URL%/api/v1/resource -H "Content-Type: application/json"
echo.
echo.

REM =====================================================================
echo [TEST 5/7] Rate Limit Test (Burst 110 requests)
REM =====================================================================
echo Sending 110 rapid requests to trigger rate limiting (limit is 100/min)...
echo Expect 429 responses after request 100.
for /L %%i in (1,1,110) do (
    curl -s -o NUL -w "Request %%i: HTTP %%{http_code}\n" %GATEWAY_URL%/api/v1/resource
)
echo.
echo.

REM =====================================================================
echo [TEST 6/7] Redis Rate Limit Keys
REM =====================================================================
echo Checking Redis for rate limit keys...
docker exec redis redis-cli KEYS "ratelimit:*"
echo.
echo.

REM =====================================================================
echo [TEST 7/7] Kafka Topics Verification
REM =====================================================================
echo Listing Kafka topics...
docker exec kafka1 kafka-topics --bootstrap-server localhost:29092 --list
echo.
echo.

echo =====================================================================
echo  ALL TESTS COMPLETED
echo =====================================================================
echo.
echo Next Steps:
echo   1. Run k6 load test: k6 run testing/load-test.js
echo   2. Check Grafana dashboards: http://localhost:3000
echo   3. Check Prometheus metrics: http://localhost:9090
echo.
pause
