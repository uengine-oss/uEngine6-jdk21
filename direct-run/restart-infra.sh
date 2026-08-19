#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$SCRIPT_DIR/../../delivery/docker-bmt-test-package-20260817/docker"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.bmt-keycloak-postgres.yml"

echo "Stopping package app containers that conflict with direct run..."
docker compose -f "$COMPOSE_FILE" stop gateway frontend process-service definition-service || true

echo "Stopping Docker containers publishing host port 5432 or 8080..."
docker ps --format '{{.Names}} {{.Ports}}' \
  | grep -E '0\.0\.0\.0:(5432|8080)->|\[::\]:(5432|8080)->' \
  | awk '{print $1}' \
  | xargs -r docker stop

for port in 5432 8080; do
  pids="$(lsof -ti "tcp:$port" 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    echo "Killing port $port: $pids"
    kill -9 $pids 2>/dev/null || true
  fi
done

echo "Starting Postgres and Keycloak only..."
docker compose -f "$COMPOSE_FILE" up -d postgres keycloak
docker compose -f "$COMPOSE_FILE" ps postgres keycloak
