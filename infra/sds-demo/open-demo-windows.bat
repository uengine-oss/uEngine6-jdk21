@echo off
echo Starting PowerShell 7 - launcher revision 20261008-E...
set "DEMO_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not exist "%DEMO_PWSH%" (
  echo ERROR: PowerShell 7 runtime is missing: %DEMO_PWSH%
  pause
  exit /b 1
)
"%DEMO_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-demo-windows.ps1" -Foreground -LoginFilePath "%~dp0..\..\..\.codex-tmp\demo-windows\login-password.xml"
if errorlevel 1 pause
