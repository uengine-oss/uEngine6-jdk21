@echo off
setlocal
call "%~dp0_env-backend.bat"
set "SERVICE_NAME=definition-service"
set "SERVICE_DIR=%SDS_BACKEND_DIR%\definition-service"
set "LOG_FILE=%~1"
if "%LOG_FILE%"=="" set "LOG_FILE=%~dp0logs\definition-service.log"
call "%~dp0_run-maven-service.bat" "%SERVICE_NAME%" "%SERVICE_DIR%" "%LOG_FILE%"
