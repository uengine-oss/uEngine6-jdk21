#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDS_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$SDS_ROOT/uEngine6-jdk21"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

pids="$(lsof -ti tcp:8088 2>/dev/null || true)"
[ -n "$pids" ] && kill -9 $pids 2>/dev/null || true

export SPRING_PROFILES_ACTIVE=keycloak-installed
export GATEWAY_PORT=8088
export GATEWAY_URI=http://localhost:8088
export FRONTEND_URI=http://localhost:5173
export KEYCLOAK_URI=http://localhost:8080
export KEYCLOAK_INTERNAL_URI=http://localhost:8080
export KEYCLOAK_REALM=uengine
export KEYCLOAK_CLIENT_ID=uengine
export KEYCLOAK_CLIENT_SECRET=66LpF19OpkpgKKpWHdgiCEKisx5AXqLA
export PROCESS_SERVICE_URI=http://localhost:9094
export DEFINITION_SERVICE_URI=http://localhost:9093
export ANALYTICS_SERVICE_URI=http://localhost:9095
export EXECUTION_SERVICE_URI=http://localhost:8200

cd "$BACKEND_DIR/keycloak-gateway"
nohup mvn -Dmaven.repo.local="$HOME/.m2/repository" -DskipTests spring-boot:run >> "$LOG_DIR/keycloak-gateway.log" 2>&1 &
echo "keycloak-gateway starting on 8088, pid=$!, log=$LOG_DIR/keycloak-gateway.log"
