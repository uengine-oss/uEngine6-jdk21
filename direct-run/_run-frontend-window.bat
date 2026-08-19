@echo off
setlocal
for %%I in ("%~dp0..\..\process-gpt-vue3-hli") do set "FRONTEND_DIR=%%~fI"
if not exist "%FRONTEND_DIR%\node_modules\.bin\vite.cmd" (
  for %%I in ("%~dp0..\..\delivery-candidate-frontend") do set "FRONTEND_DIR=%%~fI"
)
set "VITE_APP_MODE=uEngine"
set "VITE_KEYCLOAK_MODE=installed"
set "VITE_KEYCLOAK_URL=http://localhost:8080"
set "VITE_KEYCLOAK_REALM=uengine"
set "VITE_KEYCLOAK_CLIENT_ID=uengine"
set "VITE_GATEWAY_URL=http://localhost:8088"
set "VITE_DISABLE_AUTO_LAYOUT=true"
set "LOG_FILE=%~1"
if "%LOG_FILE%"=="" set "LOG_FILE=%~dp0logs\frontend.log"
call "%~dp0_run-frontend.bat" "%FRONTEND_DIR%" "%LOG_FILE%"
