@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0close-demo-windows.ps1"
if errorlevel 1 pause
