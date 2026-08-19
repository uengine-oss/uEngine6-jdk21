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

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":9094 .*LISTENING"') do taskkill /F /PID %%P >nul 2>&1

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
echo [%DATE% %TIME%] launching process-service > "%LOG_DIR%\process-service-%RUN_ID%.log"
call "%~dp0_spawn.bat" "uEngine process-service 9094" "%BACKEND_DIR%\process-service" "%~dp0_run-process-window.bat" "%LOG_DIR%\process-service-%RUN_ID%.log"
if /I "%~1"=="/nopause" exit /b 0
pause
