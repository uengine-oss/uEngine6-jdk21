"""Robot Framework library for uEngine RPA jobs.

Provides the `Set Process Output` keyword: values set here are collected
by the agent after the run and reported back to the BPM engine, where
they are assigned to ProcessVariables (via RPAActivity out-parameter
mapping).

Usage in a .robot script:

    *** Settings ***
    Library    UEngineLibrary

    *** Tasks ***
    Do Something
        Set Process Output    발송건수    120
"""

import json
import os
import re
import shutil
import sys
import time
from pathlib import Path

import requests


class UEngineLibrary:
    ROBOT_LIBRARY_SCOPE = "GLOBAL"
    ROBOT_LISTENER_API_VERSION = 2

    def __init__(self):
        # Robot 실행 종료 시 listener close() 가 불려 브라우저/영상이 반드시 flush 된다
        self.ROBOT_LIBRARY_LISTENER = self
        self._pw = None
        self._browser = None
        self._context = None
        self._page = None

    # ------------------------------------------------------------------
    # 브라우저 자동화 (Playwright) — 실행 영상 녹화 + 라이브 프레임 캡처
    # ------------------------------------------------------------------

    def open_browser(self, url, browser="chromium"):
        """Open a Playwright browser and navigate to ``url``."""
        if getattr(sys, "frozen", False):
            os.environ.setdefault("PLAYWRIGHT_BROWSERS_PATH", "0")
        try:
            from playwright.sync_api import sync_playwright
        except ImportError as exc:
            raise RuntimeError("브라우저 작업에는 playwright 설치가 필요합니다") from exc

        if self._pw is not None:
            self.close_browser()

        self._pw = sync_playwright().start()
        browser_type = getattr(self._pw, str(browser).lower(), None)
        if browser_type is None:
            self._pw.stop()
            self._pw = None
            raise ValueError(f"지원하지 않는 브라우저입니다: {browser}")

        headful = os.environ.get("UENGINE_RPA_HEADFUL") == "1"
        launch_args = {"headless": not headful}
        if headful:
            size = os.environ.get("UENGINE_SCREEN_SIZE", "1280x800").replace("x", ",")
            launch_args["args"] = ["--window-position=0,0", f"--window-size={size}"]
        try:
            self._browser = browser_type.launch(**launch_args)
        except Exception as exc:
            if not headful:
                raise
            print(f"*WARN* 헤드풀 브라우저 실행 실패({exc}) — headless 로 재시도")
            self._browser = browser_type.launch(headless=True)

        context_args = {"viewport": {"width": 1280, "height": 800}, "locale": "ko-KR"}
        video_dir = os.environ.get("UENGINE_RPA_VIDEO_DIR")
        if video_dir:
            os.makedirs(video_dir, exist_ok=True)
            context_args["record_video_dir"] = video_dir
            context_args["record_video_size"] = {"width": 1280, "height": 800}
        self._context = self._browser.new_context(**context_args)
        self._page = self._context.new_page()
        self.go_to_url(url)

    def go_to_url(self, url):
        page = self._require_page()
        page.goto(str(url), wait_until="load")
        self._capture_frame()

    def click_element(self, selector, timeout="10s"):
        page = self._require_page()
        page.locator(str(selector)).click(timeout=self._timeout_ms(timeout))
        self._capture_frame()

    def input_text(self, selector, text, clear="true"):
        page = self._require_page()
        locator = page.locator(str(selector))
        if self._as_bool(clear):
            locator.fill(str(text))
        else:
            locator.type(str(text))
        self._capture_frame()

    def select_option(self, selector, value):
        self._require_page().locator(str(selector)).select_option(str(value))
        self._capture_frame()

    def wait_for_element(self, selector, timeout="10s"):
        self._require_page().locator(str(selector)).wait_for(
            state="visible", timeout=self._timeout_ms(timeout))
        self._capture_frame()

    def save_element_text_as_output(self, selector, name):
        value = self._require_page().locator(str(selector)).inner_text()
        self.set_process_output(name, value)
        return value

    def take_browser_screenshot(self, path):
        target = self._prepare_target(path)
        self._require_page().screenshot(path=str(target), full_page=True)
        return str(target)

    def close_browser(self):
        """Close the current browser and flush recorded video."""
        if self._page is not None:
            try:
                self._capture_frame()
            except Exception:
                pass
        for closer in (
            lambda: self._context and self._context.close(),
            lambda: self._browser and self._browser.close(),
            lambda: self._pw and self._pw.stop(),
        ):
            try:
                closer()
            except Exception:
                pass
        self._pw = self._browser = self._context = self._page = None

    def _require_page(self):
        if self._page is None:
            raise RuntimeError("먼저 '브라우저 열기' 작업을 추가하세요")
        return self._page

    @staticmethod
    def _timeout_ms(value):
        text = str(value).strip().lower()
        match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*(ms|s|m)?", text)
        if not match:
            raise ValueError(f"올바르지 않은 대기시간입니다: {value}")
        number = float(match.group(1))
        unit = match.group(2) or "s"
        return number if unit == "ms" else number * (60000 if unit == "m" else 1000)

    @staticmethod
    def _as_bool(value):
        return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}

    def open_dm_site(self, url=None):
        """DM 발송 폼(/send)을 chromium 으로 연다.

        - UENGINE_RPA_VIDEO_DIR 가 설정되어 있으면 webm 영상 녹화 (실행이력용)
        - UENGINE_RPA_FRAME_FILE 이 설정되어 있으면 조작 단계마다 스크린샷 저장
          (서버 모드 라이브 스트리밍용 — 에이전트가 감지해 업로드)
        - UENGINE_RPA_HEADFUL=1 이면 화면에 보이는 브라우저 (클라이언트 데모용)
        - playwright 미설치 시 경고만 남기고 폴백 모드로 동작 (Send Dm Via Web
          이 /api/send 직접 호출)
        """
        base = os.environ.get("UENGINE_DM_SERVER", "http://localhost:7788").rstrip("/")
        if not url:
            url = f"{base}/send?agent={self._agent_label()}"
        try:
            self.open_browser(url, "chromium")
        except RuntimeError as exc:
            print(f"*WARN* {exc} — 브라우저 자동화 대신 API 폴백 모드로 동작합니다")

    def send_dm_via_web(self, customer="", account="", message="", channel="batch"):
        """발송 폼에 고객/계좌/메시지를 입력하고 [DM 발송] 을 클릭한다.

        브라우저가 없으면(playwright 미설치 또는 Open Dm Site 안 함) 기존
        `Send Dm` API 폴백으로 발송을 기록한다.
        """
        if self._page is None:
            print("*WARN* 브라우저 세션 없음 — Send Dm API 폴백 사용")
            return self.send_dm(customer, account, message, channel)

        page = self._page
        step = os.environ.get("UENGINE_RPA_STEP_DELAY")
        delay = float(step) if step else 0.4

        page.fill("#customer", "")
        page.type("#customer", str(customer), delay=30)
        self._capture_frame(); time.sleep(delay)

        page.fill("#account", "")
        page.type("#account", str(account), delay=30)
        self._capture_frame(); time.sleep(delay)

        page.fill("#message", str(message))
        self._capture_frame(); time.sleep(delay)

        page.select_option("#channel", str(channel))
        self._capture_frame()

        page.click("#sendBtn")
        page.wait_for_selector("#result.ok", timeout=10000)
        self._capture_frame(); time.sleep(delay)

        result_text = page.inner_text("#result")
        print(f"DM 발송 완료: {result_text}")
        return {"ok": True, "detail": result_text}

    def close_dm_site(self):
        """브라우저를 닫고 녹화 영상을 flush 한다 (스크립트에서 생략해도 자동 호출)."""
        self.close_browser()

    def close(self):
        """Robot listener hook — 실행 종료 시 항상 불린다."""
        self.close_browser()

    def _agent_label(self):
        return os.environ.get("UENGINE_AGENT_LABEL") \
            or ("server-docker" if os.environ.get("UENGINE_RPA_MODE") == "server" else "client-tray")

    def _capture_frame(self):
        """현재 화면을 프레임 파일에 저장 (원자적 교체) — 라이브 뷰 스트리밍용."""
        frame_file = os.environ.get("UENGINE_RPA_FRAME_FILE")
        if not frame_file or self._page is None:
            return
        try:
            data = self._page.screenshot(type="jpeg", quality=60)
            tmp = frame_file + ".tmp"
            with open(tmp, "wb") as f:
                f.write(data)
            os.replace(tmp, frame_file)
        except Exception as e:
            print(f"*WARN* 프레임 캡처 실패: {e}")

    def _output_path(self):
        return os.environ.get("UENGINE_RPA_OUTPUT", "uengine_output.json")

    def set_process_output(self, name, value):
        """Set a result value that will be assigned to a ProcessVariable."""
        path = self._output_path()
        data = {}
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except Exception:
                data = {}
        data[str(name)] = value
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)

    # ------------------------------------------------------------------
    # 데스크톱 자동화 (클라이언트 Agent)
    # ------------------------------------------------------------------

    @staticmethod
    def _desktop():
        try:
            import pyautogui
        except ImportError as exc:
            raise RuntimeError("데스크톱 작업에는 pyautogui 설치가 필요합니다") from exc
        pyautogui.FAILSAFE = True
        return pyautogui

    def click_screen(self, x, y, button="left"):
        self._desktop().click(x=int(float(x)), y=int(float(y)), button=str(button))

    def type_text(self, text, interval="0.02"):
        desktop = self._desktop()
        value = str(text)
        if value.isascii():
            desktop.write(value, interval=float(interval))
            return
        try:
            import pyperclip
        except ImportError as exc:
            raise RuntimeError("한글 데스크톱 입력에는 pyperclip 설치가 필요합니다") from exc
        pyperclip.copy(value)
        desktop.hotkey("command" if sys.platform == "darwin" else "ctrl", "v")

    def press_key(self, key):
        self._desktop().press(str(key).strip().lower())

    def press_hotkey(self, keys):
        parts = [part.strip().lower() for part in re.split(r"[+,]", str(keys)) if part.strip()]
        if len(parts) < 2:
            raise ValueError("단축키는 ctrl+s처럼 두 개 이상의 키를 입력하세요")
        self._desktop().hotkey(*parts)

    def take_desktop_screenshot(self, path):
        target = self._prepare_target(path)
        self._desktop().screenshot(str(target))
        return str(target)

    # ------------------------------------------------------------------
    # 파일 자동화
    # ------------------------------------------------------------------

    @staticmethod
    def _prepare_target(path):
        target = Path(str(path)).expanduser()
        target.parent.mkdir(parents=True, exist_ok=True)
        return target

    def read_text_file_as_output(self, path, name, encoding="utf-8"):
        value = Path(str(path)).expanduser().read_text(encoding=str(encoding))
        self.set_process_output(name, value)
        return value

    def write_text_file(self, path, content, encoding="utf-8"):
        target = self._prepare_target(path)
        target.write_text(str(content), encoding=str(encoding))
        return str(target)

    def copy_file(self, source, target):
        destination = self._prepare_target(target)
        shutil.copy2(str(Path(str(source)).expanduser()), str(destination))
        return str(destination)

    def move_file(self, source, target):
        destination = self._prepare_target(target)
        shutil.move(str(Path(str(source)).expanduser()), str(destination))
        return str(destination)

    def list_folder_as_output(self, path, name, pattern="*"):
        folder = Path(str(path)).expanduser()
        value = [str(item) for item in sorted(folder.glob(str(pattern)))]
        self.set_process_output(name, value)
        return value

    # ------------------------------------------------------------------
    # HTTP/API 및 데이터 처리
    # ------------------------------------------------------------------

    @staticmethod
    def _json_object(value, label):
        if isinstance(value, dict):
            return value
        try:
            parsed = json.loads(str(value))
        except json.JSONDecodeError as exc:
            raise ValueError(f"{label} 값은 올바른 JSON이어야 합니다") from exc
        if not isinstance(parsed, dict):
            raise ValueError(f"{label} 값은 JSON 객체여야 합니다")
        return parsed

    @staticmethod
    def _response_value(response):
        try:
            body = response.json()
        except ValueError:
            body = response.text
        return {"status": response.status_code, "headers": dict(response.headers), "body": body}

    def http_get_as_output(self, url, name, headers="{}", timeout="30"):
        response = requests.get(
            str(url), headers=self._json_object(headers, "헤더 JSON"), timeout=float(timeout))
        response.raise_for_status()
        value = self._response_value(response)
        self.set_process_output(name, value)
        return value

    def http_post_json_as_output(self, url, body, name, headers="{}", timeout="30"):
        response = requests.post(
            str(url),
            json=self._json_object(body, "본문 JSON"),
            headers=self._json_object(headers, "헤더 JSON"),
            timeout=float(timeout))
        response.raise_for_status()
        value = self._response_value(response)
        self.set_process_output(name, value)
        return value

    def json_value_as_output(self, source, path, name):
        if isinstance(source, (dict, list)):
            value = source
        else:
            try:
                value = json.loads(str(source))
            except json.JSONDecodeError as exc:
                raise ValueError("원본 JSON 값이 올바르지 않습니다") from exc

        for part in str(path).split("."):
            if isinstance(value, list):
                value = value[int(part)]
            elif isinstance(value, dict):
                if part not in value:
                    raise KeyError(f"JSON 경로를 찾을 수 없습니다: {path}")
                value = value[part]
            else:
                raise KeyError(f"JSON 경로를 찾을 수 없습니다: {path}")
        self.set_process_output(name, value)
        return value

    def send_dm(self, customer="", account="", message="", channel="batch"):
        """더미 DM 발송 시스템(/api/send)에 DM 발송을 기록한다.

        데모 페이지(rpa-agent/dm-dummy/dm_server.py)에서 실시간으로 확인된다.
        서버 주소는 UENGINE_DM_SERVER 환경변수 (기본 http://localhost:7788).
        페이지가 떠있지 않아도 잡이 실패하지는 않고 경고만 남긴다.
        """
        import urllib.request

        base = os.environ.get("UENGINE_DM_SERVER", "http://localhost:7788").rstrip("/")
        agent = os.environ.get("UENGINE_AGENT_LABEL") \
            or ("server-docker" if os.environ.get("UENGINE_RPA_MODE") == "server" else "client-tray")
        payload = json.dumps({
            "customer": customer, "account": account,
            "message": message, "channel": channel, "agent": agent,
        }, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            f"{base}/api/send", data=payload,
            headers={"Content-Type": "application/json; charset=utf-8"})
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            print(f"*WARN* DM 더미 서버({base}) 호출 실패: {e}")
            return {"ok": False, "error": str(e)}

    def get_process_output(self, name, default=None):
        """Read back a previously set output value (rarely needed)."""
        path = self._output_path()
        if not os.path.exists(path):
            return default
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f).get(str(name), default)
        except Exception:
            return default
