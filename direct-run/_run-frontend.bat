@echo off
setlocal

set "FRONTEND_DIR=%~1"
set "LOG_FILE=%~2"

if not exist "%FRONTEND_DIR%" (
  echo Frontend directory not found: %FRONTEND_DIR%
  pause
  exit /b 2
)

for %%I in ("%LOG_FILE%") do if not exist "%%~dpI" mkdir "%%~dpI"

cd /d "%FRONTEND_DIR%"
echo [%DATE% %TIME%] starting frontend > "%LOG_FILE%"
echo Directory: %CD% >> "%LOG_FILE%"
echo Command: npm run dev -- --port 5173 >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

npm run dev -- --port 5173 >> "%LOG_FILE%" 2>&1
set "RESULT=%ERRORLEVEL%"

type "%LOG_FILE%"
echo.
echo frontend exited. Error code: %RESULT%
echo Log file: %LOG_FILE%
echo Press any key to close.
pause >nul
exit /b %RESULT%
