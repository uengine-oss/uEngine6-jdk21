"""PyInstaller entrypoint for the tray agent."""
import os
import sys


def _fix_playwright_browsers_path():
    # pyinstaller-hooks-contrib 의 playwright 런타임 훅이 PLAYWRIGHT_BROWSERS_PATH=0
    # (= 번들 내부 .local-browsers 에서 브라우저를 찾음)을 setdefault 하는데, 우리는
    # 브라우저를 번들하지 않고 호스트 캐시(ms-playwright)를 쓴다. 실제 경로로 덮어써야
    # --uengine-robot 자식 프로세스의 런타임 훅 setdefault 까지 무력화된다.
    if not getattr(sys, "frozen", False):
        return
    if os.environ.get("PLAYWRIGHT_BROWSERS_PATH", "0") != "0":
        return  # 사용자가 직접 지정한 경로는 존중
    if sys.platform == "darwin":
        cache = os.path.expanduser("~/Library/Caches/ms-playwright")
    elif sys.platform == "win32":
        cache = os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "ms-playwright")
    else:
        cache = os.path.expanduser("~/.cache/ms-playwright")
    os.environ["PLAYWRIGHT_BROWSERS_PATH"] = cache


if __name__ == "__main__":
    _fix_playwright_browsers_path()
    # 냉동(frozen) 바이너리 안에서는 sys.executable 이 파이썬이 아니라 이 바이너리
    # 자신이므로, runner 가 로봇 실행을 위해 자기 자신을 --uengine-robot 플래그로
    # 재실행한다. 그 호출을 여기서 가로채 robot CLI 로 위임한다.
    if len(sys.argv) > 1 and sys.argv[1] == "--uengine-robot":
        from robot import run_cli
        run_cli(sys.argv[2:], exit=True)
    else:
        from uengine_rpa.tray import main
        main()
