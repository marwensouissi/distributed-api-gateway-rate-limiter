@echo off
REM ═══════════════════════════════════════════════════════════════════════════════
REM                    API GATEWAY TEST RUNNER (Windows)
REM ═══════════════════════════════════════════════════════════════════════════════
REM 
REM This script runs the k6 load tests against the API Gateway.
REM 
REM PREREQUISITES:
REM   1. k6 installed (https://k6.io/docs/get-started/installation/)
REM   2. Docker containers running (redis, kafka, zookeeper)
REM   3. API Gateway running on port 8033
REM   4. Backend service running on port 8034
REM   5. (Optional) metrics-service, audit-service, security-analytics
REM 
REM ═══════════════════════════════════════════════════════════════════════════════

echo.
echo ===============================================================================
echo     API GATEWAY LOAD TESTING
echo ===============================================================================
echo.

REM Check if k6 is installed
where k6 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] k6 is not installed or not in PATH!
    echo.
    echo Install k6 using one of these methods:
    echo   - choco install k6
    echo   - winget install k6
    echo   - Download from https://k6.io/docs/get-started/installation/
    echo.
    exit /b 1
)

echo [OK] k6 found
echo.

REM Check if API Gateway is reachable
echo Checking if API Gateway is running...
curl -s -o nul -w "%%{http_code}" http://localhost:8033/api/v1/resource > temp_status.txt 2>nul
set /p STATUS=<temp_status.txt
del temp_status.txt 2>nul

if "%STATUS%"=="000" (
    echo [ERROR] API Gateway is not reachable on http://localhost:8033
    echo.
    echo Make sure to start:
    echo   1. docker-compose up -d ^(Redis, Kafka, etc^)
    echo   2. java -jar api-gateway/target/api-gateway-*.jar
    echo   3. java -jar backend-service/target/backend-service-*.jar
    echo.
    exit /b 1
)

echo [OK] API Gateway is running (status: %STATUS%)
echo.

REM Show menu
echo ===============================================================================
echo     SELECT TEST TO RUN
echo ===============================================================================
echo.
echo   1. Quick Rate Limit Test    (fast, ~150 requests)
echo   2. Full Workflow Test       (comprehensive, ~2 minutes)
echo   3. Load Test Only           (sustained traffic)
echo   4. Security Test            (auth and headers)
echo   5. All Tests                (run everything)
echo.
echo   0. Exit
echo.
set /p CHOICE="Enter your choice (0-5): "

if "%CHOICE%"=="0" exit /b 0

echo.
echo ===============================================================================
echo     RUNNING TESTS
echo ===============================================================================
echo.

cd /d "%~dp0"

if "%CHOICE%"=="1" (
    echo Running Quick Rate Limit Test...
    echo.
    k6 run quick-rate-limit-test.js
)

if "%CHOICE%"=="2" (
    echo Running Full Workflow Test...
    echo.
    k6 run workflow-test.js
)

if "%CHOICE%"=="3" (
    echo Running Load Test...
    echo.
    k6 run --vus 20 --duration 60s load-test.js
)

if "%CHOICE%"=="4" (
    echo Running Security Test...
    echo.
    k6 run security-test.js
)

if "%CHOICE%"=="5" (
    echo Running All Tests...
    echo.
    echo [1/4] Quick Rate Limit Test
    k6 run quick-rate-limit-test.js
    echo.
    echo [2/4] Security Test
    k6 run security-test.js
    echo.
    echo [3/4] Workflow Test
    k6 run workflow-test.js
    echo.
    echo [4/4] Load Test
    k6 run load-test.js
)

echo.
echo ===============================================================================
echo     POST-TEST VERIFICATION
echo ===============================================================================
echo.
echo Run these commands to check Redis data:
echo.
echo   docker exec redis redis-cli KEYS "ratelimit:*"
echo   docker exec redis redis-cli KEYS "stats:*"
echo   docker exec redis redis-cli HGETALL "stats:endpoint:/api/v1/resource"
echo.
echo Check audit log:
echo   type audit_log.json
echo.
echo ===============================================================================

pause
