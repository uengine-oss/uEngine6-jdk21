@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "DOCKER_DIR=%~dp0..\..\delivery\docker-bmt-test-package-20260817\docker"
set "COMPOSE_FILE=%DOCKER_DIR%\docker-compose.bmt-keycloak-postgres.yml"

echo Stopping package app containers that conflict with direct run...
docker compose -f "%COMPOSE_FILE%" stop gateway frontend process-service definition-service
if errorlevel 1 goto failed

echo Stopping any Docker containers publishing host port 5432 or 8080...
for /f "tokens=1,*" %%A in ('docker ps --format "{{.Names}} {{.Ports}}" ^| findstr /C:"0.0.0.0:5432->" /C:"[::]:5432->" /C:"0.0.0.0:8080->" /C:"[::]:8080->"') do (
  docker stop %%A
)

call :kill_port 5432
call :kill_port 8080

echo Starting Postgres and Keycloak only...
docker compose -f "%COMPOSE_FILE%" up -d postgres keycloak
if errorlevel 1 goto failed

docker compose -f "%COMPOSE_FILE%" ps postgres keycloak
if /I "%~1"=="/nopause" exit /b 0
pause
exit /b 0

:failed
echo restart-infra failed. Error code: %ERRORLEVEL%
if /I "%~1"=="/nopause" exit /b %ERRORLEVEL%
pause
exit /b %ERRORLEVEL%

:kill_port
set "PORT=%~1"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
  echo Killing PID %%P on port %PORT%
  taskkill /F /PID %%P >nul 2>&1
)
exit /b 0
