#!/bin/sh
# Xvfb 가상 화면을 띄운 뒤 워커를 실행한다.
# 화면 크기는 UENGINE_SCREEN_SIZE (예: 1280x800) 를 따른다 — ffmpeg 녹화 크기와 일치해야 함.
set -e

SCREEN="${UENGINE_SCREEN_SIZE:-1280x800}"
Xvfb "${DISPLAY:-:99}" -screen 0 "${SCREEN}x24" -nolisten tcp &

# Xvfb 가 소켓을 열 때까지 잠깐 대기
for i in $(seq 1 20); do
    if [ -e "/tmp/.X11-unix/X${DISPLAY#:}" ]; then break; fi
    sleep 0.25
done

exec "$@"
