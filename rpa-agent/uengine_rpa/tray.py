"""Client-side RPA tray agent — single executable, lives in the system tray.

    python -m uengine_rpa.tray
    (or the PyInstaller onefile build: uengine-rpa-agent)

Configuration (first match wins):
    1. env  UENGINE_BASE_URL / UENGINE_USER / UENGINE_AGENT_ID
    2. file ~/.uengine-rpa-agent.json  {"baseUrl": "...", "user": "hong@uengine.org"}
"""

import json
import os
import subprocess
import sys
import threading

from .runner import RpaRunner, DEFAULT_BASE_URL

CONFIG_PATH = os.path.join(os.path.expanduser("~"), ".uengine-rpa-agent.json")


def ensure_playwright_browsers(on_status=None):
    """최초 실행 시 playwright 브라우저(chromium) 자동 설치.

    새 담당자 PC 에는 호스트 캐시(ms-playwright)에 브라우저가 없어 headful 실행이
    실패하므로, 폴링 시작 전에 번들된 node 드라이버로 `install chromium` 을 돌린다.
    이미 설치돼 있으면 수 초 내 no-op 으로 끝나 매 기동마다 불러도 안전하다.
    설치 경로는 PLAYWRIGHT_BROWSERS_PATH (frozen 이면 tray_entry 가 호스트 캐시로
    보정)를 그대로 따른다. 실패해도 에이전트는 계속 뜬다 — 잡 실행 시
    UEngineLibrary 가 API 폴백으로 동작하고 로그에 원인이 남는다.
    """
    try:
        from playwright._impl._driver import compute_driver_executable, get_driver_env
    except ImportError:
        return  # playwright 미포함 빌드 — API 폴백 모드로만 동작
    try:
        node, cli = compute_driver_executable()
        print("[rpa-agent] playwright 브라우저 확인/설치 중 (chromium)...", flush=True)
        if on_status:
            on_status("browser setup")
        proc = subprocess.run(
            [node, cli, "install", "chromium"],
            env=get_driver_env(), capture_output=True, text=True, timeout=900,
        )
        if proc.returncode == 0:
            print("[rpa-agent] playwright 브라우저 준비 완료", flush=True)
        else:
            tail = (proc.stderr or proc.stdout or "").strip()[-500:]
            print(f"[rpa-agent] playwright install 실패 (rc={proc.returncode}): {tail}", flush=True)
    except Exception as e:
        print(f"[rpa-agent] playwright install 오류: {e}", flush=True)
    finally:
        if on_status:
            on_status("idle")


def load_config():
    cfg = {}
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
        except Exception:
            cfg = {}
    return {
        "baseUrl": os.environ.get("UENGINE_BASE_URL") or cfg.get("baseUrl") or DEFAULT_BASE_URL,
        "user": os.environ.get("UENGINE_USER") or cfg.get("user"),
        "agentId": os.environ.get("UENGINE_AGENT_ID") or cfg.get("agentId"),
    }


def make_icon_image(color):
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, 60, 60), fill=color)
    d.text((22, 18), "R", fill="white")
    return img


def main():
    cfg = load_config()
    status = {"text": "idle"}

    def on_status(text):
        status["text"] = text
        try:
            icon.icon = make_icon_image("#2e7d32" if text == "idle" else "#f9a825")
            icon.update_menu()
        except Exception:
            pass

    runner = RpaRunner(
        base_url=cfg["baseUrl"],
        mode="client",
        user=cfg["user"],
        agent_id=cfg["agentId"],
        on_status=on_status,
    )

    # UENGINE_TRAY_HEADLESS=1: 트레이 아이콘 없이 폴링 루프만 (백그라운드/nohup 실행용 —
    # macOS에서 GUI 세션 없는 프로세스의 pystray icon.run()이 조용히 종료되며
    # 데몬 워커까지 같이 죽는 문제 회피)
    if os.environ.get("UENGINE_TRAY_HEADLESS") == "1":
        print("[rpa-agent] UENGINE_TRAY_HEADLESS=1, running headless client agent")
        ensure_playwright_browsers()
        runner.run_forever()
        return

    try:
        import pystray
    except ImportError:
        # tray libs unavailable (e.g. headless test) — run in console mode
        print("[rpa-agent] pystray not available, running headless client agent")
        ensure_playwright_browsers()
        runner.run_forever()
        return

    def worker():
        # 아이콘은 즉시 띄우고, 브라우저 설치(최초 1회 수 분)는 워커 스레드에서 —
        # 설치가 끝나야 폴링을 시작하므로 반쯤 준비된 상태로 잡을 집지 않는다
        ensure_playwright_browsers(on_status=on_status)
        runner.run_forever()

    worker_thread = threading.Thread(target=worker, daemon=True)

    def on_toggle_pause(icon_, item):
        runner.pause(not runner.paused)
        icon_.update_menu()

    def on_quit(icon_, item):
        runner.stop()
        icon_.stop()

    icon = pystray.Icon(
        "uengine-rpa-agent",
        make_icon_image("#2e7d32"),
        "uEngine RPA Agent",
        menu=pystray.Menu(
            pystray.MenuItem(lambda item: f"Status: {status['text']}", None, enabled=False),
            pystray.MenuItem(lambda item: f"User: {cfg['user'] or '(any)'}", None, enabled=False),
            pystray.MenuItem(lambda item: "Resume" if runner.paused else "Pause", on_toggle_pause),
            pystray.MenuItem("Quit", on_quit),
        ),
    )

    worker_thread.start()
    icon.run()
    sys.exit(0)


if __name__ == "__main__":
    main()
