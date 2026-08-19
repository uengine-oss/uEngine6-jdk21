#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDS_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$SDS_ROOT/uEngine6-jdk21"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

pids="$(lsof -ti tcp:9094 2>/dev/null || true)"
[ -n "$pids" ] && kill -9 $pids 2>/dev/null || true

export SPRING_PROFILES_ACTIVE=postgres,keycloak-installed
export PROCESS_SERVICE_PORT=9094
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=uengine
export POSTGRES_USER=uengine
export POSTGRES_PASSWORD=uengine
export UENGINE_DB_SCHEMA=public
export UENGINE_BASEPATH="$SDS_ROOT/delivery/docker-bmt-test-package-20260817/process-assets"
export UENGINE_DEFINITION_BASEPATH="$SDS_ROOT/delivery/docker-bmt-test-package-20260817/process-assets"
export KEYCLOAK_URI=http://localhost:8080

cd "$BACKEND_DIR/process-service"
nohup mvn -Dmaven.repo.local="$HOME/.m2/repository" -DskipTests spring-boot:run >> "$LOG_DIR/process-service.log" 2>&1 &
echo "process-service starting on 9094, pid=$!, log=$LOG_DIR/process-service.log"
