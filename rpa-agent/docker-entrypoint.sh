#!/bin/sh
# Xvfb 가상 화면을 띄운 뒤 워커를 실행한다.
# 화면 크기는 UENGINE_SCREEN_SIZE (예: 1280x800) 를 따른다 — ffmpeg 녹화 크기와 일치해야 함.
set -e

SCREEN="${UENGINE_SCREEN_SIZE:-1280x800}"
DISPLAY_NO="${DISPLAY:-:99}"
DISPLAY_NUM="${DISPLAY_NO#:}"

# 이전 컨테이너 종료가 깨끗하지 않으면 /tmp/.X99-lock 또는 소켓만 남고
# 실제 Xvfb 프로세스는 없는 상태가 된다. 이 상태를 준비 완료로 보면
# Chromium/ffmpeg 가 "Cannot open display" 로 실패하므로 시작 전에 정리한다.
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}"

Xvfb "${DISPLAY_NO}" -screen 0 "${SCREEN}x24" -nolisten tcp &

# Xvfb 가 소켓을 열 때까지 잠깐 대기
for i in $(seq 1 20); do
    if [ -e "/tmp/.X11-unix/X${DISPLAY_NUM}" ]; then break; fi
    sleep 0.25
done

exec "$@"
