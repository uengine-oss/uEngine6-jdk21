"""Core runner shared by the server worker (Docker) and the client tray agent.

Polls the process-service RPA job queue, executes claimed jobs with
Robot Framework, streams logs back, and reports the final result so the
BPM engine can assign values to ProcessVariables and continue the
process.
"""

import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import uuid

import requests

DEFAULT_BASE_URL = os.environ.get("UENGINE_BASE_URL", "http://localhost:9094")
POLL_INTERVAL = float(os.environ.get("UENGINE_POLL_INTERVAL", "3"))


class RpaRunner:
    def __init__(self, base_url=DEFAULT_BASE_URL, mode="server", user=None,
                 agent_id=None, on_status=None, log=print):
        self.base_url = base_url.rstrip("/")
        self.mode = mode
        self.user = user
        self.agent_id = agent_id or f"{mode}-{uuid.uuid4().hex[:8]}"
        self.on_status = on_status or (lambda status: None)
        self.log = log
        self._stop = threading.Event()
        self._paused = threading.Event()
        self.current_job = None

    # ------------------------------------------------------------------ control

    def stop(self):
        self._stop.set()

    def pause(self, paused=True):
        if paused:
            self._paused.set()
        else:
            self._paused.clear()

    @property
    def paused(self):
        return self._paused.is_set()

    # ------------------------------------------------------------------ REST

    def _poll(self):
        params = {"agentId": self.agent_id, "mode": self.mode}
        if self.user:
            params["user"] = self.user
        r = requests.post(f"{self.base_url}/rpa/poll", params=params, timeout=15)
        r.raise_for_status()
        data = r.json()
        return data if data.get("jobId") else None

    def _post_start(self, job_id):
        requests.post(f"{self.base_url}/rpa/jobs/{job_id}/start", timeout=15)

    def _post_log(self, job_id, chunk):
        if not chunk:
            return
        try:
            requests.post(f"{self.base_url}/rpa/jobs/{job_id}/log",
                          data=chunk.encode("utf-8"),
                          headers={"Content-Type": "text/plain; charset=utf-8"},
                          timeout=15)
        except Exception as e:
            self.log(f"[rpa-agent] log post failed: {e}")

    def _post_complete(self, job_id, success, result, error):
        body = {"success": success, "result": result or {}, "error": error}
        r = requests.post(f"{self.base_url}/rpa/jobs/{job_id}/complete",
                          json=body, timeout=30)
        r.raise_for_status()

    def _post_frame(self, job_id, data):
        try:
            requests.post(f"{self.base_url}/rpa/jobs/{job_id}/frame", data=data,
                          headers={"Content-Type": "image/jpeg"}, timeout=10)
        except Exception:
            pass  # 라이브 뷰 프레임 유실은 치명적이지 않음

    def _post_video(self, job_id, path):
        try:
            size = os.path.getsize(path)
            if size == 0 or size > 50 * 1024 * 1024:
                self.log(f"[rpa-agent] video skipped (size={size})")
                return
            with open(path, "rb") as f:
                requests.post(f"{self.base_url}/rpa/jobs/{job_id}/video",
                              data=f.read(),
                              headers={"Content-Type": "video/webm"}, timeout=60)
            self.log(f"[rpa-agent] video uploaded ({size} bytes)")
        except Exception as e:
            self.log(f"[rpa-agent] video upload failed: {e}")

    # ------------------------------------------------------------------ screen capture
    #
    # 서버 모드에서 DISPLAY(가상 화면, 예: Docker 의 Xvfb)와 ffmpeg 가 있으면
    # 로봇이 조작하는 "화면 전체"를 녹화(실행이력 영상)하고 1초 간격 프레임을
    # 올려 라이브 스트리밍한다 — 웹 자동화든 데스크톱 자동화든 무관하게 동작.
    # 그 환경이 없으면 UEngineLibrary 의 Playwright 녹화/프레임 캡처로 폴백한다.

    def _start_screen_capture(self, job_id, workdir):
        display = os.environ.get("DISPLAY")
        if self.mode != "server" or not display or not shutil.which("ffmpeg"):
            return None

        size = os.environ.get("UENGINE_SCREEN_SIZE", "1280x800")
        video_path = os.path.join(workdir, "screen.webm")
        frame_path = os.path.join(workdir, "frame.jpg")
        # libvpx 는 realtime deadline 이 아니면 인코딩이 실시간보다 느려 프레임이
        # 밀리고, 종료 시 백로그 flush 가 오래 걸려 0바이트 영상이 남는다.
        cmd = ["ffmpeg", "-y", "-loglevel", "error",
               "-f", "x11grab", "-framerate", "5", "-video_size", size,
               "-i", display,
               "-map", "0:v", "-c:v", "libvpx", "-b:v", "600k",
               "-deadline", "realtime", "-cpu-used", "8",
               "-pix_fmt", "yuv420p", video_path,
               "-map", "0:v", "-vf", "fps=1", "-update", "1", "-q:v", "6",
               frame_path]
        try:
            errfile = open(os.path.join(workdir, "ffmpeg.err"), "wb")
            proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL,
                                    stderr=errfile)
        except Exception as e:
            self.log(f"[rpa-agent] screen capture start failed: {e}")
            return None

        stop_evt = threading.Event()
        uploader = threading.Thread(
            target=self._frame_upload_loop, args=(job_id, frame_path, stop_evt),
            daemon=True)
        uploader.start()
        self.log(f"[rpa-agent] screen capture started (display={display}, {size})")
        return {"proc": proc, "video": video_path, "frame": frame_path,
                "stop": stop_evt, "thread": uploader, "errfile": errfile}

    def _frame_upload_loop(self, job_id, frame_path, stop_evt):
        last_mtime = 0
        while not stop_evt.wait(1.0):
            try:
                mtime = os.path.getmtime(frame_path)
            except OSError:
                continue
            if mtime == last_mtime:
                continue
            last_mtime = mtime
            try:
                with open(frame_path, "rb") as f:
                    data = f.read()
                if data:
                    self._post_frame(job_id, data)
            except OSError:
                continue

    def _stop_screen_capture(self, cap):
        if not cap:
            return None
        cap["stop"].set()
        proc = cap["proc"]
        early_rc = proc.poll()
        try:
            proc.send_signal(signal.SIGINT)  # ffmpeg graceful stop (파일 마무리)
            proc.wait(timeout=20)
        except Exception as e:
            self.log(f"[rpa-agent] ffmpeg graceful stop failed ({e}) — killing")
            proc.kill()
            try:
                proc.wait(timeout=3)
            except Exception:
                pass
        cap["thread"].join(timeout=3)
        try:
            cap["errfile"].close()
        except Exception:
            pass

        video = cap["video"] if os.path.exists(cap["video"]) else None
        if early_rc is not None or not video or os.path.getsize(video) == 0:
            if early_rc is not None:
                self.log(f"[rpa-agent] ffmpeg had already exited rc={early_rc}")
            try:
                with open(cap["errfile"].name, "r", errors="replace") as f:
                    tail = f.read()[-1000:]
                if tail.strip():
                    self.log(f"[rpa-agent] ffmpeg stderr: {tail}")
            except Exception:
                pass
        return video

    def _find_fallback_video(self, video_dir):
        """Playwright record_video_dir 폴백으로 남은 최신 webm 을 찾는다."""
        if not video_dir or not os.path.isdir(video_dir):
            return None
        webms = [os.path.join(video_dir, f) for f in os.listdir(video_dir)
                 if f.endswith(".webm")]
        return max(webms, key=os.path.getmtime) if webms else None

    # ------------------------------------------------------------------ job run

    def _run_job(self, job):
        job_id = job["jobId"]
        self.current_job = job
        self.on_status(f"running: {job.get('activityName') or job_id}")
        self.log(f"[rpa-agent] running job {job_id} ({job.get('activityName')})")

        workdir = tempfile.mkdtemp(prefix=f"uengine-rpa-{job_id[:8]}-")
        output_path = os.path.join(workdir, "uengine_output.json")
        robot_file = os.path.join(workdir, "job.robot")
        capture = None
        fallback_frame_stop = None

        try:
            self._post_start(job_id)

            script = job.get("script") or ""
            if not script.strip():
                self._post_complete(job_id, False, None, "empty robot script")
                return

            with open(robot_file, "w", encoding="utf-8") as f:
                f.write(script)

            lib_dir = os.path.dirname(os.path.abspath(__file__))

            # PyInstaller 단일 실행파일에서는 sys.executable 이 파이썬이 아니라
            # 에이전트 바이너리 자신이므로 "-m robot" 이 동작하지 않는다.
            # tray_entry.py 가 --uengine-robot 플래그를 가로채 robot CLI 로 위임한다.
            if getattr(sys, "frozen", False):
                cmd = [sys.executable, "--uengine-robot"]
            else:
                cmd = [sys.executable, "-m", "robot"]
            cmd += ["--outputdir", workdir,
                    "--consolecolors", "off",
                    "--pythonpath", lib_dir]
            for key, value in (job.get("inputs") or {}).items():
                cmd += ["--variable", f"{key}:{'' if value is None else value}"]
            cmd.append(robot_file)

            env = dict(os.environ)
            env["UENGINE_RPA_OUTPUT"] = output_path
            # UEngineLibrary.Send Dm 등에서 실행 주체(서버/클라이언트)를 표시할 수 있게 전달
            env["UENGINE_RPA_MODE"] = self.mode
            env.setdefault("UENGINE_AGENT_LABEL",
                           f"{'server' if self.mode == 'server' else 'client'}:{self.agent_id}")
            # make UEngineLibrary importable from the script
            env["PYTHONPATH"] = lib_dir + os.pathsep + env.get("PYTHONPATH", "")

            # ---- 실행화면 캡처 (영상 녹화 + 라이브 프레임) ----
            capture = self._start_screen_capture(job_id, workdir)
            if capture:
                # 가상 화면(Xvfb)에 실제 창을 띄워야 화면 녹화에 잡힌다
                env["UENGINE_RPA_HEADFUL"] = "1"
            else:
                # 화면 캡처 환경 없음 → UEngineLibrary(Playwright) 녹화/프레임 폴백
                env.setdefault("UENGINE_RPA_VIDEO_DIR", os.path.join(workdir, "video"))
                if self.mode == "server":
                    frame_path = os.path.join(workdir, "frame.jpg")
                    env.setdefault("UENGINE_RPA_FRAME_FILE", frame_path)
                    fallback_frame_stop = threading.Event()
                    threading.Thread(
                        target=self._frame_upload_loop,
                        args=(job_id, env["UENGINE_RPA_FRAME_FILE"], fallback_frame_stop),
                        daemon=True).start()
                else:
                    # 클라이언트: 유저 PC 화면에서 실제 동작이 보이도록 헤드풀
                    env.setdefault("UENGINE_RPA_HEADFUL", "1")
            video_dir = env.get("UENGINE_RPA_VIDEO_DIR")

            timeout = int(job.get("timeoutSeconds") or 600)
            proc = subprocess.Popen(cmd, cwd=workdir, env=env,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT,
                                    text=True, encoding="utf-8", errors="replace")

            deadline = time.time() + timeout
            buffer = []
            last_flush = time.time()
            timed_out = False

            while True:
                line = proc.stdout.readline()
                if line:
                    buffer.append(line)
                    self.log(line.rstrip())
                    if time.time() - last_flush > 2 or len(buffer) > 50:
                        self._post_log(job_id, "".join(buffer))
                        buffer, last_flush = [], time.time()
                elif proc.poll() is not None:
                    break
                if time.time() > deadline:
                    timed_out = True
                    proc.kill()
                    break

            if buffer:
                self._post_log(job_id, "".join(buffer))

            # ---- 캡처 종료 + 실행 영상 업로드 (complete 이전 — 완료 시점에 영상이 보이도록) ----
            video_path = self._stop_screen_capture(capture)
            capture = None
            if fallback_frame_stop:
                fallback_frame_stop.set()
                fallback_frame_stop = None
            if not video_path:
                video_path = self._find_fallback_video(video_dir)
            if video_path:
                self._post_video(job_id, video_path)

            result = {}
            if os.path.exists(output_path):
                try:
                    with open(output_path, "r", encoding="utf-8") as f:
                        result = json.load(f)
                except Exception as e:
                    self.log(f"[rpa-agent] bad output json: {e}")

            if timed_out:
                self._post_complete(job_id, False, result,
                                    f"robot run timed out after {timeout}s")
            elif proc.returncode == 0:
                self._post_complete(job_id, True, result, None)
                self.log(f"[rpa-agent] job {job_id} DONE, result={result}")
            else:
                self._post_complete(job_id, False, result,
                                    f"robot exited with code {proc.returncode}")
                self.log(f"[rpa-agent] job {job_id} FAILED rc={proc.returncode}")

        except Exception as e:
            self.log(f"[rpa-agent] job {job_id} error: {e}")
            try:
                self._post_complete(job_id, False, None, str(e))
            except Exception:
                pass
        finally:
            if capture:
                self._stop_screen_capture(capture)
            if fallback_frame_stop:
                fallback_frame_stop.set()
            self.current_job = None
            shutil.rmtree(workdir, ignore_errors=True)
            self.on_status("idle")

    # ------------------------------------------------------------------ loop

    def run_forever(self):
        self.log(f"[rpa-agent] started agentId={self.agent_id} mode={self.mode}"
                 f" user={self.user or '-'} server={self.base_url}")
        self.on_status("idle")
        while not self._stop.is_set():
            if self._paused.is_set():
                time.sleep(1)
                continue
            try:
                job = self._poll()
            except Exception as e:
                self.log(f"[rpa-agent] poll failed: {e}")
                job = None
                time.sleep(min(POLL_INTERVAL * 3, 15))
            if job:
                self._run_job(job)
            else:
                time.sleep(POLL_INTERVAL)
        self.log("[rpa-agent] stopped")
