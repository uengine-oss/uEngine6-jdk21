#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

pids="$(lsof -ti tcp:9095 -sTCP:LISTEN 2>/dev/null || true)"
[ -n "$pids" ] && kill -9 $pids 2>/dev/null || true

export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home)"
export ANALYTICS_SERVICE_PORT=9095
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=uengine
export POSTGRES_USER=uengine
export POSTGRES_PASSWORD=uengine
export UENGINE_DB_SCHEMA=public
export UENGINE_ANALYTICS_ETL_ENABLED=true

cd "$PROJECT_ROOT/analytics"
nohup mvn -Dmaven.repo.local="$HOME/.m2/repository" -Dmaven.test.skip=true spring-boot:run \
  >> "$LOG_DIR/analytics.log" 2>&1 &
echo "analytics starting on 9095, pid=$!, log=$LOG_DIR/analytics.log"
