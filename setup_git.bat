@echo off
echo Initializing Git...
git init
if %errorlevel% neq 0 echo Failed to init git & exit /b %errorlevel%

echo Renaming branch to main...
git branch -M main

echo Adding remote...
git remote remove origin
git remote add origin https://github.com/marwensouissi/distributed-api-gateway-rate-limiter.git

echo Committing 1/8...
git add README.md .gitignore
git commit -m "Initial commit: Project documentation and gitignore"

echo Committing 2/8...
git add docker-compose.yml infra/ pom.xml
git commit -m "feat: Add infrastructure (Docker, Helm, Root POM)"

echo Committing 3/8...
git add api-gateway/
git commit -m "feat(gateway): Implement API Gateway with rate limiting"

echo Committing 4/8...
git add backend-service/
git commit -m "feat(backend): Add Backend Service"

echo Committing 5/8...
git add audit-service/
git commit -m "feat(audit): Implement Audit Service for request logging"

echo Committing 6/8...
git add metrics-service/
git commit -m "feat(metrics): Implement Metrics Service for aggregation"

echo Committing 7/8...
git add security-analytics/
git commit -m "feat(security): Implement Security Analytics Service"

echo Committing 8/8...
git add testing/
git commit -m "test: Add load and security tests (k6)"

echo Pushing to GitHub...
git push -u origin main
if %errorlevel% neq 0 echo Push failed! & exit /b %errorlevel%

echo SUCCESS: Project pushed to GitHub.
