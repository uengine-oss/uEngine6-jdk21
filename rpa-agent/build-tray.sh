#!/usr/bin/env bash
# Build the client tray agent as a single executable (PyInstaller onefile).
# Result: dist/uengine-rpa-agent (macOS/Linux) or dist/uengine-rpa-agent.exe (Windows)
set -e
cd "$(dirname "$0")"

python3 -m venv .venv-build 2>/dev/null || true
source .venv-build/bin/activate
pip install -q -r requirements-tray.txt
# 브라우저는 번들하지 않는다 — 호스트 공유 캐시(~/Library/Caches/ms-playwright)에 설치.
# PLAYWRIGHT_BROWSERS_PATH=0 으로 패키지 안(.local-browsers)에 설치하면 PyInstaller 가
# Chrome 서명 바이너리 처리에 실패하고 onefile 도 수백 MB 가 된다. frozen 실행 시
# 캐시 경로 보정은 tray_entry.py 의 _fix_playwright_browsers_path 가 담당한다.
rm -rf .venv-build/lib/python*/site-packages/playwright/driver/package/.local-browsers
python -m playwright install chromium

pyinstaller --noconfirm --onefile --windowed \
  --name uengine-rpa-agent \
  --collect-all robot \
  --collect-all playwright \
  --collect-all pyautogui \
  --collect-all pyperclip \
  --add-data "uengine_rpa/UEngineLibrary.py:uengine_rpa" \
  --hidden-import uengine_rpa.UEngineLibrary \
  tray_entry.py

echo "Built: dist/uengine-rpa-agent"
