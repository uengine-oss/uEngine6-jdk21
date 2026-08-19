@echo off
setlocal
cd /d "%~dp0"

echo Starting direct-run environment.
echo   infra:    Docker postgres 5432, keycloak 8080
echo   backend:  Maven definition 9093, process 9094, gateway 8088
echo   frontend: npm dev 5173
echo.

call restart-infra.bat /nopause
if errorlevel 1 goto failed

call restart-definition.bat /nopause
timeout /t 3 /nobreak >nul
call restart-process.bat /nopause
timeout /t 3 /nobreak >nul
call restart-frontend.bat /nopause
timeout /t 3 /nobreak >nul
call restart-gateway.bat /nopause

echo.
echo Direct-run windows opened.
echo Open after boot: http://localhost:8088
echo Login: hong / 1234
echo.
if /I "%~1"=="/nopause" exit /b 0
pause
exit /b 0

:failed
echo run-all failed. Error code: %ERRORLEVEL%
if /I "%~1"=="/nopause" exit /b %ERRORLEVEL%
pause
exit /b %ERRORLEVEL%
