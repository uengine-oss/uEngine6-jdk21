"""E2E: SDS_DepositBalanceNotice — DM 발송 데모 검증.

usage:
  python3 e2e_dm_demo.py Y            # 일괄 branch → 서버(Docker) RPA
  python3 e2e_dm_demo.py N            # 개별 branch → 클라이언트(트레이) RPA
  python3 e2e_dm_demo.py Y --resume 7 # 기존 인스턴스 이어서
"""
import base64
import json
import sys
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:9094'
DM = 'http://localhost:7788'

BATCH = (sys.argv[1] if len(sys.argv) > 1 else 'Y').upper()
RESUME = sys.argv[3] if len(sys.argv) > 3 and sys.argv[2] == '--resume' else None

TITLE_PARAMS = {
    '예금잔액 통보 대상 게시': {'본부 일괄발송 예금계좌': BATCH},
    'DM 수령 및 확인': {'예금잔액 이상': 'N'},
}


def b64(d):
    return base64.urlsafe_b64encode(json.dumps(d).encode()).rstrip(b'=').decode()


TOKEN = f"{b64({'alg': 'none', 'typ': 'JWT'})}.{b64({'email': 'hong@uengine.org', 'preferred_username': 'hong', 'sub': 'hong'})}.sig"


def req(method, url, body=None):
    h = {'Authorization': f'Bearer {TOKEN}', 'Content-Type': 'application/json;charset=UTF-8'}
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(url if url.startswith('http') else BASE + url,
                               data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            t = resp.read().decode()
            try:
                return resp.status, json.loads(t) if t.strip() else None
            except Exception:
                return resp.status, t[:500]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:500]


def todo(iid):
    st, data = req('GET', '/worklist/search/findToDo')
    if not isinstance(data, dict):
        return []
    out = []
    for it in data.get('_embedded', {}).get('worklist', []):
        if str(it.get('instId')) != str(iid):
            continue
        href = it.get('_links', {}).get('self', {}).get('href', '')
        out.append({'taskId': href.rstrip('/').split('/')[-1],
                    'name': it.get('title') or '', 'status': it.get('status') or ''})
    return out


def rpa_jobs(iid):
    st, jobs = req('GET', f'/rpa/jobs?instanceId={iid}')
    return jobs if isinstance(jobs, list) else []


def inst_status(iid):
    st, r = req('GET', f'/instance/{iid}/status/0')
    return r if isinstance(r, dict) else {}


# ---- start (or resume) instance
if RESUME:
    iid = RESUME
    print('resuming instance', iid, 'branch=', BATCH)
else:
    st, inst = req('POST', '/instance', {'processDefinitionId': 'SDS_DepositBalanceNotice', 'simulation': False})
    iid = str(inst.get('instanceId') or inst.get('id'))
    print(f'started instance {iid} (본부 일괄발송 예금계좌={BATCH} → {"서버 RPA" if BATCH == "Y" else "클라이언트 RPA"} 경로)')

done = set()
deadline = time.time() + 180
while time.time() < deadline:
    items = [w for w in todo(iid) if w['taskId'] not in done]
    if items:
        w = items[0]
        params = TITLE_PARAMS.get(w['name'], {})
        print(f"human task: {w['name']}")
        req('POST', f"/work-item/{w['taskId']}/claim", {'endpoint': 'hong@uengine.org'})
        st, r = req('POST', f"/work-item/{w['taskId']}/complete", {'parameterValues': params})
        print(f"    complete params={params}: {st} {r if st >= 400 else ''}")
        if st >= 400:
            break
        done.add(w['taskId'])
        continue
    jobs = rpa_jobs(iid)
    active = [j for j in jobs if j.get('status') in ('QUEUED', 'CLAIMED', 'RUNNING')]
    if active:
        j = active[0]
        print(f"    RPA job {j.get('jobId', '')[:8]} [{j.get('activityName')}] {j.get('status')} ...")
        time.sleep(3)
        continue
    st, inst = req('GET', f'/instances/{iid}')
    if isinstance(inst, dict) and inst.get('status') == 'Completed':
        print('instance completed.')
        break
    time.sleep(3)

print()
print('=== RPA jobs of instance', iid, '===')
for j in rpa_jobs(iid):
    print(f"  {j.get('jobId', '')[:8]} [{j.get('activityName')}] mode={j.get('mode') or j.get('executionType')} status={j.get('status')} agent={j.get('agentId')}")
st, variables = req('GET', f'/instance/{iid}/variables')
if isinstance(variables, dict):
    keep = {k: v for k, v in variables.items() if ':' not in k}
    print('process variables:', json.dumps(keep, ensure_ascii=False))

st, sent = req('GET', DM + '/api/sent')
print(f'=== DM dummy page: {len(sent) if isinstance(sent, list) else "?"} DMs ===')
if isinstance(sent, list):
    for x in sent:
        print(f"  [{x['time']}] {x['channel']:6s} {x['customer']:8s} {x['account']:16s} agent={x['agent']} | {x['message']}")
