@echo off
setlocal

set "SERVICE_NAME=%~1"
set "SERVICE_DIR=%~2"
set "LOG_FILE=%~3"

if "%SERVICE_NAME%"=="" (
  echo Missing SERVICE_NAME
  pause
  exit /b 2
)

if not exist "%SERVICE_DIR%" (
  echo Service directory not found: %SERVICE_DIR%
  pause
  exit /b 2
)

for %%I in ("%LOG_FILE%") do if not exist "%%~dpI" mkdir "%%~dpI"

cd /d "%SERVICE_DIR%"
echo [%DATE% %TIME%] starting %SERVICE_NAME% > "%LOG_FILE%"
echo Directory: %CD% >> "%LOG_FILE%"
echo Command: mvn -Dmaven.repo.local=%MAVEN_REPO% -DskipTests spring-boot:run >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

mvn -Dmaven.repo.local=%MAVEN_REPO% -DskipTests spring-boot:run >> "%LOG_FILE%" 2>&1
set "RESULT=%ERRORLEVEL%"

type "%LOG_FILE%"
echo.
echo %SERVICE_NAME% exited. Error code: %RESULT%
echo Log file: %LOG_FILE%
echo Press any key to close.
pause >nul
exit /b %RESULT%
