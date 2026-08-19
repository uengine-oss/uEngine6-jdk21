#!/usr/bin/env bash
# 로컬 설치 검증 스크립트
#
#   Keycloak 로그인 → 게이트웨이 세션 → 정의 조회 → 인스턴스 시작 → 워크리스트 → 작업 완료
#
# 사전 조건: docs/local-install-guide.md 의 1~6 단계를 마친 상태
#   - PostgreSQL 5432 / Keycloak 8280 / gateway 8288 / definition 9293 / process 9294 기동
#   - 프로세스를 시작하는 계정에 manager 역할 부여 (브랜치 realm-export 는 hong 에 부여돼 있음)
#   - definitions/test/test.bpmn(0.2), definitions/test/testCall.bpmn(1.0) 아카이브 생성
#
# 사용: bash docs/local/verify-install.sh [사용자] [비밀번호]
#   기본값은 브랜치 bmt/sds-process-test 의 포트(8288/8280)와 manager 역할 보유 계정(hong/1234)
set -u

GW=${GATEWAY_URI:-http://localhost:8288}
KC=${KEYCLOAK_URI:-http://localhost:8280}
USER_ID=${1:-hong}
USER_PW=${2:-1234}
USER_ENDPOINT=${USER_ENDPOINT:-hong@uengine.org}   # JWT email 클레임 값과 일치해야 한다
JAR=$(mktemp)
C="curl -s -c $JAR -b $JAR"
fail() { echo "  ✗ $1"; exit 1; }

echo "[1] 게이트웨이 OAuth2 로그인"
$C -o /dev/null "$GW/"
AUTH=$($C -o /dev/null -w '%{redirect_url}' "$GW/oauth2/authorization/keycloak")
[ -n "$AUTH" ] || fail "로그인 리다이렉트 없음. 게이트웨이 프로파일이 keycloak-installed 인지 확인"
FORM=$($C "$AUTH" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//;s/"$//;s/&amp;/\&/g')
[ -n "$FORM" ] || fail "Keycloak 로그인 폼 없음. realm sslRequired 설정 확인"
CB=$($C -o /dev/null -w '%{redirect_url}' --data-urlencode "username=$USER_ID" --data-urlencode "password=$USER_PW" "$FORM")
case "$CB" in *"/login/oauth2/code/keycloak"*) ;; *) fail "인증 실패 (자격증명/redirect URI 확인)";; esac
$C -o /dev/null "$CB"
echo "  ✓ 세션 발급"

echo "[2] 액세스 토큰 발급"
TOKEN=$(curl -s -d client_id=uengine -d "username=$USER_ID" -d "password=$USER_PW" \
             -d grant_type=password -d scope=openid \
             "$KC/realms/uengine/protocol/openid-connect/token" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')
[ -n "$TOKEN" ] || fail "토큰 발급 실패"
python3 -c "
import base64, json
p = '$TOKEN'.split('.')[1]; p += '=' * (-len(p) % 4)
d = json.loads(base64.urlsafe_b64decode(p))
print('  ✓', d.get('preferred_username'), '/', d.get('email'), '/ roles:', d.get('realm_access', {}).get('roles'))
assert 'manager' in d.get('realm_access', {}).get('roles', []), '  ✗ manager 역할이 없다 (설치 가이드 4-3)'
"
AUTHH="Authorization: Bearer $TOKEN"

echo "[3] 정의 목록 조회"
CODE=$($C -o /dev/null -w '%{http_code}' -H "$AUTHH" "$GW/definition/")
[ "$CODE" = "200" ] || fail "/definition/ HTTP $CODE (definition-service 확인)"
echo "  ✓ HTTP 200"

echo "[4] 프로세스 인스턴스 시작 (test/test)"
RES=$($C -X POST "$GW/instance" -H "$AUTHH" -H 'Content-Type: application/json;charset=UTF-8' -d "{
  \"processDefinitionId\": \"test/test\",
  \"roleMappings\": [
    {\"name\": \"신고자\", \"endpoints\": [\"$USER_ENDPOINT\"], \"resourceNames\": [\"신고자\"]},
    {\"name\": \"관리자\", \"endpoints\": [\"$USER_ENDPOINT\"], \"resourceNames\": [\"관리자\"]}
  ]}")
IID=$(echo "$RES" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("instanceId",""))' 2>/dev/null)
[ -n "$IID" ] || fail "인스턴스 시작 실패: $(echo "$RES" | head -c 300)"
echo "  ✓ instanceId=$IID"

echo "[5] 워크리스트 확인"
WI=$($C -H "$AUTHH" "$GW/worklist" | python3 -c "
import sys, json
d = json.load(sys.stdin)
ids = [w['_links']['self']['href'].split('/')[-1] for w in d['_embedded']['worklist'] if str(w['instId']) == '$IID']
print(ids[0] if ids else '')")
[ -n "$WI" ] || fail "새 인스턴스의 작업이 워크리스트에 없다"
echo "  ✓ workItem=$WI"

echo "[6] 작업 완료 (폼 값 포함)"
CODE=$($C -o /dev/null -w '%{http_code}' -X POST "$GW/work-item/$WI/complete" -H "$AUTHH" \
  -H 'Content-Type: application/json;charset=UTF-8' \
  -d '{"desiredState":"complete","parameterValues":{"신고내용":[{"_type":"org.uengine.contexts.HtmlFormContext","formDefId":"고장내용","filePath":"고장내용.form","valueMap":{"_type":"java.util.HashMap","고장":{"_type":"java.util.HashMap","고장유형":"hw","고장내용":"설치 검증"}}}]}}')
[ "$CODE" = "200" ] || fail "완료 실패 HTTP $CODE (--add-opens / archive 생성 여부 확인)"
echo "  ✓ HTTP 200 — 다음 액티비티 생성됨"

rm -f "$JAR"
echo
echo "설치 검증 완료."
