#!/usr/bin/env bash
# RPA 에이전트 2종 재기동:
#  1) 서버 사이드: Docker 컨테이너 rpa-worker (uengine/rpa-worker 이미지)
#  2) 클라이언트 사이드: 로컬 venv 트레이 에이전트 (headless 폴백 동작)
# 사전조건: process-service(9094) 기동, docker image 빌드(rpa-agent/Dockerfile),
#           rpa-agent/.venv-build (build-tray.sh 가 만들거나 수동 생성)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RPA_AGENT_DIR="$(cd "$SCRIPT_DIR/../rpa-agent" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

# ---- server-side worker (Docker) ----
docker rm -f rpa-worker >/dev/null 2>&1 || true
docker run -d --name rpa-worker \
  -e UENGINE_BASE_URL=http://host.docker.internal:9094 \
  -e UENGINE_DM_SERVER=http://host.docker.internal:7788 \
  -e UENGINE_AGENT_ID=server-docker-1 \
  uengine/rpa-worker >/dev/null
echo "server worker: docker container 'rpa-worker' (logs: docker logs -f rpa-worker)"

# ---- client-side tray agent ----
# 기본: PyInstaller .app 을 open 으로 실행 → 메뉴바에 트레이 아이콘(R)이 보인다.
#       (설정은 ~/.uengine-rpa-agent.json — open 은 셸 env 를 전달하지 않음)
# 폴백: .app 이 없거나 UENGINE_TRAY_HEADLESS=1 이면 venv headless 실행 (아이콘 없음).
pkill -f "uengine_rpa.tray" 2>/dev/null || true
pkill -f "uengine-rpa-agent" 2>/dev/null || true

APP="$RPA_AGENT_DIR/dist/uengine-rpa-agent.app"
CONFIG="$HOME/.uengine-rpa-agent.json"
if [ -d "$APP" ] && [ "${UENGINE_TRAY_HEADLESS:-0}" != "1" ]; then
  if [ ! -f "$CONFIG" ]; then
    cat > "$CONFIG" <<'EOF'
{
  "baseUrl": "http://localhost:9094",
  "user": "hong@uengine.org",
  "agentId": "client-tray-1"
}
EOF
    echo "created $CONFIG"
  fi
  open -n "$APP"
  echo "client tray agent: $APP (메뉴바 R 아이콘, config: $CONFIG)"
else
  PY="$RPA_AGENT_DIR/.venv-build/bin/python"
  if [ ! -x "$PY" ]; then
    echo "venv 없음 → 생성 중 ($RPA_AGENT_DIR/.venv-build)"
    python3 -m venv "$RPA_AGENT_DIR/.venv-build"
    "$RPA_AGENT_DIR/.venv-build/bin/pip" install -q -r "$RPA_AGENT_DIR/requirements-tray.txt"
  fi
  cd "$RPA_AGENT_DIR"
  nohup env PYTHONUNBUFFERED=1 \
    UENGINE_BASE_URL=http://localhost:9094 \
    UENGINE_USER=hong@uengine.org \
    UENGINE_AGENT_ID=client-tray-1 \
    UENGINE_TRAY_HEADLESS=1 \
    "$PY" -m uengine_rpa.tray >> "$LOG_DIR/rpa-client.log" 2>&1 &
  disown || true
  echo "client tray agent: local venv headless, pid=$! (log: $LOG_DIR/rpa-client.log)"
fi
