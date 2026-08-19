"""Dummy 'SDS DM 발송 시스템' — RPA 가 실제로 두드리는 데모용 웹페이지.

표준 라이브러리만 사용한다 (의존성 없음).

  python3 dm_server.py            # http://localhost:7788
  DM_PORT=9999 python3 dm_server.py

엔드포인트
  GET  /            : DM 발송 현황 페이지 (1.5초 폴링, 새 행 하이라이트)
  GET  /send        : DM 발송 폼 페이지 — RPA 로봇이 실제 브라우저로 입력·클릭하는 화면
  GET  /api/sent    : 발송 내역 JSON (최신순)
  POST /api/send    : {"account","customer","message","channel","agent"} 발송 기록 추가
  POST /api/clear   : 내역 초기화 (데모 반복용)

RPA 로봇은 UEngineLibrary 의 `Send Dm Via Web` 키워드로 /send 폼을 브라우저 자동화로
조작하며 (실행 영상 녹화됨), 폴백으로 `Send Dm` 키워드가 /api/send 를 직접 호출한다.
Docker 서버 워커에서는 UENGINE_DM_SERVER=http://host.docker.internal:7788 로 접근.
"""

import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("DM_PORT", "7788"))

_lock = threading.Lock()
_sent = []          # newest first
_seq = 0

PAGE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>DM 발송 시스템 (더미)</title>
<style>
  body { font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif;
         margin: 0; background: #f4f6fa; color: #222; }
  header { background: #1e3a8a; color: #fff; padding: 14px 24px;
           display: flex; align-items: center; gap: 12px; }
  header h1 { font-size: 18px; margin: 0; font-weight: 600; }
  header .live { font-size: 12px; background: #16a34a; border-radius: 10px;
                 padding: 2px 10px; animation: pulse 1.6s infinite; }
  @keyframes pulse { 50% { opacity: .55; } }
  .wrap { max-width: 980px; margin: 24px auto; padding: 0 16px; }
  .cards { display: flex; gap: 16px; margin-bottom: 20px; }
  .card { flex: 1; background: #fff; border-radius: 10px; padding: 16px 20px;
          box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  .card .num { font-size: 30px; font-weight: 700; }
  .card .lbl { font-size: 13px; color: #666; margin-top: 2px; }
  table { width: 100%; border-collapse: collapse; background: #fff;
          border-radius: 10px; overflow: hidden;
          box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  th, td { padding: 10px 14px; font-size: 13px; text-align: left;
           border-bottom: 1px solid #eef1f6; }
  th { background: #f8fafc; color: #555; font-weight: 600; }
  tr.fresh { animation: flash 2.5s ease-out; }
  @keyframes flash { 0% { background: #fef9c3; } 100% { background: #fff; } }
  .badge { display: inline-block; border-radius: 8px; padding: 2px 8px;
           font-size: 12px; color: #fff; }
  .badge.batch { background: #2563eb; }
  .badge.single { background: #7c3aed; }
  .badge.server { background: #0d9488; }
  .badge.client { background: #d97706; }
  .empty { text-align: center; color: #999; padding: 40px 0; }
  button { border: 0; background: #e2e8f0; border-radius: 8px;
           padding: 6px 14px; cursor: pointer; font-size: 12px; }
</style>
</head>
<body>
<header>
  <h1>DM 발송 시스템 <span style="font-weight:300">(더미)</span></h1>
  <span class="live">LIVE</span>
  <span style="flex:1"></span>
  <button onclick="clearAll()">내역 초기화</button>
</header>
<div class="wrap">
  <div class="cards">
    <div class="card"><div class="num" id="c-total">0</div><div class="lbl">총 발송</div></div>
    <div class="card"><div class="num" id="c-batch">0</div><div class="lbl">일괄 DM (서버 RPA)</div></div>
    <div class="card"><div class="num" id="c-single">0</div><div class="lbl">개별 DM (클라이언트 RPA)</div></div>
  </div>
  <table>
    <thead><tr>
      <th>시간</th><th>구분</th><th>고객</th><th>계좌</th><th>메시지</th><th>처리 에이전트</th>
    </tr></thead>
    <tbody id="rows"><tr><td colspan="6" class="empty">아직 발송된 DM 이 없습니다 — RPA 실행을 기다리는 중…</td></tr></tbody>
  </table>
</div>
<script>
let known = new Set();
async function refresh() {
  try {
    const res = await fetch('/api/sent');
    const list = await res.json();
    const rows = document.getElementById('rows');
    document.getElementById('c-total').textContent = list.length;
    document.getElementById('c-batch').textContent = list.filter(x => x.channel === 'batch').length;
    document.getElementById('c-single').textContent = list.filter(x => x.channel !== 'batch').length;
    if (list.length === 0) {
      rows.innerHTML = '<tr><td colspan="6" class="empty">아직 발송된 DM 이 없습니다 — RPA 실행을 기다리는 중…</td></tr>';
      known = new Set();
      return;
    }
    rows.innerHTML = list.map(x => {
      const fresh = !known.has(x.id) ? ' class="fresh"' : '';
      const ch = x.channel === 'batch'
        ? '<span class="badge batch">일괄</span>' : '<span class="badge single">개별</span>';
      const ag = (x.agent || '').indexOf('server') >= 0
        ? '<span class="badge server">' + x.agent + '</span>'
        : '<span class="badge client">' + (x.agent || '-') + '</span>';
      return '<tr' + fresh + '><td>' + x.time + '</td><td>' + ch + '</td><td>' +
             (x.customer || '-') + '</td><td>' + (x.account || '-') + '</td><td>' +
             (x.message || '-') + '</td><td>' + ag + '</td></tr>';
    }).join('');
    list.forEach(x => known.add(x.id));
  } catch (e) { /* server restarting */ }
}
async function clearAll() { await fetch('/api/clear', {method: 'POST'}); refresh(); }
refresh();
setInterval(refresh, 1500);
</script>
</body>
</html>
"""


SEND_PAGE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>DM 발송 시스템 — DM 발송</title>
<style>
  body { font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif;
         margin: 0; background: #f4f6fa; color: #222; }
  header { background: #1e3a8a; color: #fff; padding: 14px 24px;
           display: flex; align-items: center; gap: 12px; }
  header h1 { font-size: 18px; margin: 0; font-weight: 600; }
  .wrap { max-width: 560px; margin: 32px auto; padding: 0 16px; }
  .panel { background: #fff; border-radius: 12px; padding: 24px 28px;
           box-shadow: 0 1px 4px rgba(0,0,0,.1); }
  .panel h2 { font-size: 16px; margin: 0 0 18px; }
  label { display: block; font-size: 13px; color: #555; margin: 14px 0 4px; }
  input, select, textarea { width: 100%; box-sizing: border-box; font-size: 14px;
           border: 1px solid #cbd5e1; border-radius: 8px; padding: 9px 12px;
           background: #fff; font-family: inherit; }
  input:focus, select:focus, textarea:focus { outline: 2px solid #2563eb44; }
  button { width: 100%; margin-top: 22px; border: 0; border-radius: 8px;
           background: #1e3a8a; color: #fff; font-size: 15px; font-weight: 600;
           padding: 12px; cursor: pointer; }
  button:hover { background: #27439b; }
  #result { margin-top: 16px; border-radius: 8px; padding: 12px 14px;
            font-size: 14px; display: none; }
  #result.ok { display: block; background: #dcfce7; color: #166534; }
  #result.err { display: block; background: #fee2e2; color: #991b1b; }
  .hint { font-size: 12px; color: #94a3b8; margin-top: 14px; text-align: center; }
</style>
</head>
<body>
<header>
  <h1>DM 발송 시스템 <span style="font-weight:300">(더미)</span></h1>
  <span style="flex:1"></span>
  <a href="/" style="color:#c7d2fe; font-size:13px; text-decoration:none">발송 현황 →</a>
</header>
<div class="wrap">
  <div class="panel">
    <h2>DM 발송</h2>
    <label for="customer">고객명</label>
    <input id="customer" placeholder="예: 홍길동">
    <label for="account">계좌번호</label>
    <input id="account" placeholder="예: 110-234-10011">
    <label for="message">메시지</label>
    <textarea id="message" rows="3" placeholder="발송할 DM 내용"></textarea>
    <label for="channel">발송 구분</label>
    <select id="channel">
      <option value="batch">일괄 (batch)</option>
      <option value="single">개별 (single)</option>
    </select>
    <button id="sendBtn" onclick="send()">DM 발송</button>
    <div id="result"></div>
    <div class="hint">이 화면은 RPA 로봇이 브라우저 자동화로 조작하는 데모용 폼입니다.</div>
  </div>
</div>
<script>
async function send() {
  const result = document.getElementById('result');
  const agent = new URLSearchParams(location.search).get('agent') || 'web-form';
  const payload = {
    customer: document.getElementById('customer').value,
    account: document.getElementById('account').value,
    message: document.getElementById('message').value,
    channel: document.getElementById('channel').value,
    agent: agent,
  };
  try {
    const res = await fetch('/api/send', {
      method: 'POST',
      headers: {'Content-Type': 'application/json; charset=utf-8'},
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    result.className = 'ok';
    result.textContent = '발송 완료 (접수번호 ' + data.id + ') — ' +
        payload.customer + ' / ' + payload.account;
  } catch (e) {
    result.className = 'err';
    result.textContent = '발송 실패: ' + e;
  }
}
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/sent":
            with _lock:
                self._json(200, list(_sent))
        elif self.path in ("/", "/index.html") or self.path.startswith("/send"):
            body = (SEND_PAGE if self.path.startswith("/send") else PAGE).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        global _seq
        if self.path == "/api/send":
            length = int(self.headers.get("Content-Length") or 0)
            try:
                data = json.loads(self.rfile.read(length) or b"{}")
            except Exception:
                data = {}
            with _lock:
                _seq += 1
                rec = {
                    "id": _seq,
                    "time": time.strftime("%H:%M:%S"),
                    "channel": data.get("channel") or "batch",
                    "customer": data.get("customer") or "",
                    "account": data.get("account") or "",
                    "message": data.get("message") or "",
                    "agent": data.get("agent") or "",
                }
                _sent.insert(0, rec)
            self._json(200, {"ok": True, "id": rec["id"]})
        elif self.path == "/api/clear":
            with _lock:
                _sent.clear()
            self._json(200, {"ok": True})
        else:
            self._json(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        print("[dm-dummy]", fmt % args)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[dm-dummy] SDS DM 발송 시스템(더미) listening on http://localhost:{PORT}")
    server.serve_forever()
