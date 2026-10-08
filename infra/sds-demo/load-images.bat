@echo off
setlocal
cd /d "%~dp0"

set IMAGE_TAR=images\uengine-bmt-runtime-images.tar
set LOG_DIR=logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set LOG_FILE=%LOG_DIR%\load-images.log

if not exist "images\uengine-process-service-update.tar" (
  echo Required verified process-service update archive is missing. Refusing old runtime fallback.
  exit /b 1
)

if not exist "%IMAGE_TAR%" (
  echo Image tar not found: %CD%\%IMAGE_TAR%
  echo.
  pause
  exit /b 1
)

echo [%DATE% %TIME%] docker load -i "%IMAGE_TAR%" > "%LOG_FILE%"
docker load -i "%IMAGE_TAR%" 1>> "%LOG_FILE%" 2>&1
set RESULT=%ERRORLEVEL%
rem Apply verified updates after the older bundled runtime images.
if "%RESULT%"=="0" if exist "images\uengine-process-service-update.tar" (
  docker load -i "images\uengine-process-service-update.tar" 1>> "%LOG_FILE%" 2>&1
  if errorlevel 1 set RESULT=1
)
if "%RESULT%"=="0" if exist "images\uengine-corebank-web-update.tar" (
  docker load -i "images\uengine-corebank-web-update.tar" 1>> "%LOG_FILE%" 2>&1
  if errorlevel 1 set RESULT=1
)
if "%RESULT%"=="0" if exist "images\uengine-analytics-update.tar" (
  docker load -i "images\uengine-analytics-update.tar" 1>> "%LOG_FILE%" 2>&1
  if errorlevel 1 set RESULT=1
)
if "%RESULT%"=="0" if exist "images\uengine-frontend-update.tar" (
  docker load -i "images\uengine-frontend-update.tar" 1>> "%LOG_FILE%" 2>&1
  if errorlevel 1 set RESULT=1
)
if "%RESULT%"=="0" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-process-runtime.ps1" 1>> "%LOG_FILE%" 2>&1
  if errorlevel 1 set RESULT=1
)
type "%LOG_FILE%"
echo.
if "%RESULT%"=="0" (
  echo Docker image load completed.
) else (
  echo Docker image load failed. Error code: %RESULT%
  echo Failed command: docker load -i "%IMAGE_TAR%"
  echo Log file: %CD%\%LOG_FILE%
)
if "%~1"=="/nopause" exit /b %RESULT%
echo Press any key to close this window.
pause >nul
exit /b %RESULT%
