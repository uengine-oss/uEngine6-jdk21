#!/usr/bin/env bash
# 더미 "SDS DM 발송 시스템" 페이지 (RPA 가 실제로 DM 을 기록하는 데모 서버)
# http://localhost:7788
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RPA_AGENT_DIR="$(cd "$SCRIPT_DIR/../rpa-agent" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

pids="$(lsof -ti tcp:7788 2>/dev/null || true)"
[ -n "$pids" ] && kill -9 $pids 2>/dev/null || true

nohup python3 "$RPA_AGENT_DIR/dm-dummy/dm_server.py" >> "$LOG_DIR/dm-dummy.log" 2>&1 &
echo "DM dummy page on http://localhost:7788, pid=$!, log=$LOG_DIR/dm-dummy.log"
