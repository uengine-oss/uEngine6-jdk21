#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDS_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$SDS_ROOT/process-gpt-vue3-hli"
if [ ! -x "$FRONTEND_DIR/node_modules/.bin/vite" ] && [ -x "$SDS_ROOT/delivery-candidate-frontend/node_modules/.bin/vite" ]; then
  FRONTEND_DIR="$SDS_ROOT/delivery-candidate-frontend"
fi
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

pids="$(lsof -ti tcp:5173 2>/dev/null || true)"
[ -n "$pids" ] && kill -9 $pids 2>/dev/null || true

export VITE_APP_MODE=uEngine
export VITE_KEYCLOAK_MODE=installed
export VITE_KEYCLOAK_URL=http://localhost:8080
export VITE_KEYCLOAK_REALM=uengine
export VITE_KEYCLOAK_CLIENT_ID=uengine
export VITE_GATEWAY_URL=http://localhost:8088
export VITE_DISABLE_AUTO_LAYOUT=true

cd "$FRONTEND_DIR"
nohup npm run dev -- --port 5173 >> "$LOG_DIR/frontend.log" 2>&1 &
echo "frontend starting on 5173, pid=$!, log=$LOG_DIR/frontend.log"
