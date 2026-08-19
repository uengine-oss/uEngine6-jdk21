@echo off
setlocal

set "TITLE=%~1"
set "WORK_DIR=%~2"
set "RUNNER=%~3"
set "LOG_FILE=%~4"

if "%TITLE%"=="" exit /b 2
if not exist "%WORK_DIR%" exit /b 2
if not exist "%RUNNER%" exit /b 2

start "%TITLE%" /D "%WORK_DIR%" "%RUNNER%" "%LOG_FILE%"
exit /b 0
