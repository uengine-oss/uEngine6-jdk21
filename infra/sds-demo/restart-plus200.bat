@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set COMPOSE_PROJECT=bmt-plus200
set COMPOSE_FILE=docker-compose.bmt-keycloak-postgres.plus200.yml
set REALM_FILE=keycloak\realm-export.plus200.json
set KEYCLOAK_BACKUP_DIR=backups\keycloak-plus200
set POSTGRES_BACKUP_DIR=backups\postgres-plus200
set LOG_DIR=logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set LOG_FILE=%LOG_DIR%\restart-plus200.log
echo [%DATE% %TIME%] restart-plus200 started > "%LOG_FILE%"

echo [%DATE% %TIME%] checking administrator permission >> "%LOG_FILE%"
net session >nul 2>> "%LOG_FILE%"
if errorlevel 1 (
  echo Warning: Administrator permission not available. WinNAT reset skipped. >> "%LOG_FILE%"
  echo Warning: Administrator permission not available. WinNAT reset skipped.
) else (
  echo Resetting Windows NAT service before Docker restart...
  echo [%DATE% %TIME%] net stop winnat >> "%LOG_FILE%"
  net stop winnat 1>> "%LOG_FILE%" 2>&1
  echo [%DATE% %TIME%] net start winnat >> "%LOG_FILE%"
  net start winnat 1>> "%LOG_FILE%" 2>&1
)

set TS=%DATE%-%TIME%
set TS=%TS:/=-%
set TS=%TS:.=-%
set TS=%TS::=-%
set TS=%TS: =0%

if not exist "%KEYCLOAK_BACKUP_DIR%" mkdir "%KEYCLOAK_BACKUP_DIR%"
if not exist "%POSTGRES_BACKUP_DIR%" mkdir "%POSTGRES_BACKUP_DIR%"

if exist "%REALM_FILE%" (
  set REALM_BACKUP=%KEYCLOAK_BACKUP_DIR%\realm-export-plus200-%TS%.json
  copy /Y "%REALM_FILE%" "!REALM_BACKUP!" >nul
  echo Backed up Keycloak plus200 realm import file: %CD%\!REALM_BACKUP!
) else (
  echo Warning: Keycloak plus200 realm import file not found: %CD%\%REALM_FILE%
)

set DB_BACKUP=%POSTGRES_BACKUP_DIR%\uengine-postgres-plus200-%TS%.sql
echo Backing up plus200 Postgres DB, including Keycloak runtime users: %CD%\%DB_BACKUP%
echo [%DATE% %TIME%] docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" exec -T postgres pg_dump -U uengine -d uengine >> "%LOG_FILE%"
docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" exec -T postgres pg_dump -U uengine -d uengine > "%DB_BACKUP%" 2>> "%LOG_FILE%"
if errorlevel 1 (
  del "%DB_BACKUP%" >nul 2>nul
  echo Warning: Postgres DB backup skipped or failed. If this is the first run before services exist, start once and run restart again.
  echo Warning: Postgres DB backup skipped or failed. >> "%LOG_FILE%"
) else (
  echo Backed up plus200 Postgres DB: %CD%\%DB_BACKUP%
  echo Backed up plus200 Postgres DB: %CD%\%DB_BACKUP% >> "%LOG_FILE%"
)

echo Stopping plus200 Docker services without removing volumes...
echo [%DATE% %TIME%] docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" down >> "%LOG_FILE%"
docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" down 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo Loading packaged Docker images before plus200 restart...
echo [%DATE% %TIME%] call load-images.bat /nopause >> "%LOG_FILE%"
call load-images.bat /nopause 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo [%DATE% %TIME%] docker image inspect uengine-process-service:keycloak-postgres >> "%LOG_FILE%"
docker image inspect uengine-process-service:keycloak-postgres --format "process-service image: {{.Id}} created={{.Created}}" 1>> "%LOG_FILE%" 2>&1

echo Starting plus200 Docker services...
echo [%DATE% %TIME%] docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" up -d --force-recreate >> "%LOG_FILE%"
docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" up -d --force-recreate 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo Waiting for process-service health on localhost:9294...
echo [%DATE% %TIME%] waiting for process-service health >> "%LOG_FILE%"
set HEALTH_OK=
for /L %%I in (1,1,60) do (
  curl.exe -fsS --max-time 3 http://localhost:9294/actuator/health 1>> "%LOG_FILE%" 2>&1
  if not errorlevel 1 (
    set HEALTH_OK=1
    goto process_service_ready
  )
  ping -n 6 127.0.0.1 >nul
)

:process_service_ready
if not defined HEALTH_OK (
  echo process-service did not become reachable from gateway within 5 minutes. >> "%LOG_FILE%"
  goto failed
)

echo Checking the running process-service image and JAR version...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-process-runtime.ps1" -Running 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo Recreating gateway after process-service is reachable to clear stale route connections...
echo [%DATE% %TIME%] docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" up -d --force-recreate gateway >> "%LOG_FILE%"
docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" up -d --force-recreate gateway 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo [%DATE% %TIME%] call patch-keycloak-manager.bat plus200 >> "%LOG_FILE%"
call patch-keycloak-manager.bat plus200 1>> "%LOG_FILE%" 2>&1
if errorlevel 1 goto failed

echo Current plus200 service status:
echo [%DATE% %TIME%] docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" ps >> "%LOG_FILE%"
docker compose -p "%COMPOSE_PROJECT%" -f "%COMPOSE_FILE%" ps 1>> "%LOG_FILE%" 2>&1
type "%LOG_FILE%"

echo.
echo Open: http://localhost:8288
echo Corebank: http://localhost:5374
echo Login: use the configured demo account.
echo.
echo Restart plus200 completed. Press any key to close this window.
pause >nul
exit /b 0

:failed
set RESULT=%ERRORLEVEL%
type "%LOG_FILE%"
echo.
echo Restart plus200 failed. Error code: %RESULT%
echo Failed command is shown near the bottom of this log:
echo Log file: %CD%\%LOG_FILE%
echo Press any key to close this window.
pause >nul
exit /b %RESULT%
