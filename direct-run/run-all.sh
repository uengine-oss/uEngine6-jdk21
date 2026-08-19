#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

bash "$SCRIPT_DIR/restart-infra.sh"
sleep 5
bash "$SCRIPT_DIR/restart-definition.sh"
sleep 3
bash "$SCRIPT_DIR/restart-process.sh"
sleep 3
bash "$SCRIPT_DIR/restart-analytics.sh"
sleep 3
bash "$SCRIPT_DIR/restart-frontend.sh"
sleep 3
bash "$SCRIPT_DIR/restart-gateway.sh"
sleep 3
bash "$SCRIPT_DIR/restart-dm-dummy.sh"
bash "$SCRIPT_DIR/restart-rpa-agents.sh"

echo
echo "Direct-run commands issued."
echo "Open after boot: http://localhost:8088  (login: hong / 1234)"
echo "DM demo page:    http://localhost:7788"
echo "Logs on Mac/Linux: $SCRIPT_DIR/logs"
