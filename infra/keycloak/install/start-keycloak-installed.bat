@echo off
setlocal

set "KEYCLOAK_HOME=%~dp0keycloak-26.6.1"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
  )
)

if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if not defined KC_BOOTSTRAP_ADMIN_USERNAME set "KC_BOOTSTRAP_ADMIN_USERNAME=admin"
if not defined KC_BOOTSTRAP_ADMIN_PASSWORD set "KC_BOOTSTRAP_ADMIN_PASSWORD=admin"
if not defined KEYCLOAK_HTTP_PORT set "KEYCLOAK_HTTP_PORT=8080"

cd /d "%KEYCLOAK_HOME%"
bin\kc.bat start-dev --http-port=%KEYCLOAK_HTTP_PORT% --import-realm

