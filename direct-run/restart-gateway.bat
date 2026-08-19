@echo off
setlocal
cd /d "%~dp0"

for %%I in ("%~dp0..") do set "BACKEND_DIR=%%~fI"
set "LOG_DIR=%~dp0logs"
set "RUN_ID=%DATE%_%TIME%"
set "RUN_ID=%RUN_ID:/=-%"
set "RUN_ID=%RUN_ID::=-%"
set "RUN_ID=%RUN_ID:.=-%"
set "RUN_ID=%RUN_ID: =0%"

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":8088 .*LISTENING"') do taskkill /F /PID %%P >nul 2>&1

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
echo [%DATE% %TIME%] launching keycloak-gateway > "%LOG_DIR%\keycloak-gateway-%RUN_ID%.log"
call "%~dp0_spawn.bat" "uEngine keycloak-gateway 8088" "%BACKEND_DIR%\keycloak-gateway" "%~dp0_run-gateway-window.bat" "%LOG_DIR%\keycloak-gateway-%RUN_ID%.log"
if /I "%~1"=="/nopause" exit /b 0
pause
