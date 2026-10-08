@echo off
setlocal
cd /d "%~dp0"
"%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore-dmn-normal.ps1"
set RESULT=%ERRORLEVEL%
echo.
pause
exit /b %RESULT%
