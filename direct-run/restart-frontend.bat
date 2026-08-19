@echo off
setlocal
cd /d "%~dp0"

for %%I in ("%~dp0..\..\process-gpt-vue3-hli") do set "FRONTEND_DIR=%%~fI"
if not exist "%FRONTEND_DIR%\node_modules\.bin\vite.cmd" (
  for %%I in ("%~dp0..\..\delivery-candidate-frontend") do set "FRONTEND_DIR=%%~fI"
)
set "LOG_DIR=%~dp0logs"
set "RUN_ID=%DATE%_%TIME%"
set "RUN_ID=%RUN_ID:/=-%"
set "RUN_ID=%RUN_ID::=-%"
set "RUN_ID=%RUN_ID:.=-%"
set "RUN_ID=%RUN_ID: =0%"

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":5173 .*LISTENING"') do taskkill /F /PID %%P >nul 2>&1

set "VITE_APP_MODE=uEngine"
set "VITE_KEYCLOAK_MODE=installed"
set "VITE_KEYCLOAK_URL=http://localhost:8080"
set "VITE_KEYCLOAK_REALM=uengine"
set "VITE_KEYCLOAK_CLIENT_ID=uengine"
set "VITE_GATEWAY_URL=http://localhost:8088"
set "VITE_DISABLE_AUTO_LAYOUT=true"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
echo [%DATE% %TIME%] launching frontend from %FRONTEND_DIR% > "%LOG_DIR%\frontend-%RUN_ID%.log"
call "%~dp0_spawn.bat" "uEngine frontend 5173" "%FRONTEND_DIR%" "%~dp0_run-frontend-window.bat" "%LOG_DIR%\frontend-%RUN_ID%.log"
if /I "%~1"=="/nopause" exit /b 0
pause
