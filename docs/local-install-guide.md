# uEngine6 BPM 로컬 설치·기동 매뉴얼 (앱은 직접 실행 / 인프라만 Docker)

공식 설치 문서(<https://bpm-intro.uengine.io/bpm6-install/>)의 "로컬 환경 설치" 경로를
**실제로 끝까지 실행해 보고** 막히는 지점을 모두 메꾼 매뉴얼이다.
막힌 지점의 원인과 근본 조치 필요 여부는 [install-issues.md](install-issues.md) 에 별도로 관리한다.

- 대상 브랜치: **`bmt/sds-process-test`** (`dbb05bb6 Prepare SDS BMT backend runtime`)
- 검증 일자: 2026-08-19
- 검증 환경: macOS 26 (Apple Silicon), Docker Desktop, Homebrew
- 검증 범위: **Keycloak 로그인 → 게이트웨이 → 프로세스 정의 조회 → 인스턴스 시작 → 워크리스트 → 작업 완료(다음 액티비티 생성)** 까지 성공
- 미검증: 프론트엔드 UI (별도 저장소, 아래 9절)

> **전부 Docker 로 띄우려면** 이 문서 대신 [keycloak-docker-postgres-mode.md](keycloak-docker-postgres-mode.md)
> (`infra/docker-compose.keycloak-postgres.yml`) 를 쓰면 된다.
> 이 문서는 **애플리케이션을 호스트에서 직접(`java -jar`) 실행**하고 인프라(DB·Keycloak)만 Docker 로 띄우는 방식이다.

> 공식 문서와 다른 점: 공식 문서는 Kafka 를 먼저 띄우라고 안내하지만
> 현재 코드의 기본 메시징 모드는 `polling`(DB inbox 기반)이라 **Kafka 없이 동작한다.**
> 대신 공식 문서에 없는 **DB(PostgreSQL)** 가 반드시 필요하다.

---

## 0. 구성도와 포트

이 브랜치는 dev/qa/prod 기본 포트를 건드리지 않으려고 `keycloak-installed` 프로파일에서
**포트를 한 단계 옮겨 쓴다.** compose 모드와 동일한 번호를 그대로 따른다.

| 구성요소 | 포트 | 실행 방식 | 비고 |
|---|---|---|---|
| PostgreSQL | 5432 | Docker | compose 모드는 5632 |
| Keycloak | 8280 | Docker | |
| keycloak-gateway | 8288 | `java -jar` | 브라우저 진입점 |
| definition-service | 9293 | `java -jar` | |
| process-service | 9294 | `java -jar` | |
| frontend (process-gpt-vue3-hli) | 5373 | 별도 저장소 | 이 저장소에 없음 |

브라우저 → `http://localhost:8288` → (Keycloak 로그인) → 게이트웨이가 라우팅
- `/definition/**`, `/version(s)/**` → definition-service:9293
- `/instance/**`, `/work-item/**`, `/worklist/**`, `/instances/**` → process-service:9294
- `/notifications/**` → process-service:9294 (헤더 벨/배지 알림)
- 그 외 `/**` → frontend:5373

---

## 1. 사전 준비

### 1-1. JDK 21

`pom.xml` 의 `<java.version>21</java.version>` 이므로 **JDK 21 이 필수**다. 17 로는 빌드되지 않는다.

```bash
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # openjdk version "21.x"
```

### 1-2. 작업 디렉터리 고정

모든 명령은 저장소 루트에서 실행한다. 정의 파일 경로가 이 값을 기준으로 잡힌다.

```bash
cd /path/to/uEngine6-jdk21
git checkout bmt/sds-process-test
export UENGINE_BASEPATH="$(pwd)"
```

---

## 2. 빌드

```bash
mvn -B -Dmaven.test.skip=true install                                 # process/definition + 코어 모듈
mvn -B -Dmaven.test.skip=true -f keycloak-gateway/pom.xml package     # 게이트웨이는 루트 모듈이 아님
```

> ⚠️ `-DskipTests` 가 아니라 **`-Dmaven.test.skip=true`** 를 써야 한다.
> `-DskipTests` 는 테스트 *컴파일*은 수행하는데, `process-service` 의 테스트 소스가
> 본 소스와 어긋나 컴파일 에러가 난다 ([install-issues.md](install-issues.md) #2).
> 브랜치를 갈아탄 직후에는 `clean install` 로 전체 재컴파일할 것.

생성물:
- `process-service/target/process-service-1.1-SNAPSHOT.jar`
- `definition-service/target/definition-service-1.1-SNAPSHOT.jar`
- `keycloak-gateway/target/gateway-0.0.1-SNAPSHOT.jar`

---

## 3. PostgreSQL 기동 (Docker)

```bash
docker run -d --name uengine-postgres \
  -e POSTGRES_USER=uengine -e POSTGRES_PASSWORD=uengine -e POSTGRES_DB=uengine \
  -p 5432:5432 -v uengine-postgres-data:/var/lib/postgresql/data \
  -v "$(pwd)/infra/postgres/init:/docker-entrypoint-initdb.d:ro" \
  postgres:16-alpine

until docker exec uengine-postgres pg_isready -U uengine -d uengine; do sleep 2; done
```

`infra/docker-compose.postgres.yml` 을 써도 되지만, Compose 가 네트워크를 만들지 못하면
(`all predefined address pools have been fully subnetted`) `docker network prune` 이 필요하다.
위처럼 `docker run` 으로 띄우면 새 네트워크를 만들지 않는다.

스키마는 Hibernate `ddl-auto: update` 가 기동 시 생성한다.

> **Oracle 을 쓰려는 경우**: `oracle/docker-compose.yml` 의 `gvenzl/oracle-xe:21-slim` 은
> amd64 전용이라 Apple Silicon 에서 `ORA-00443` 로 죽는다. arm64 네이티브인
> `gvenzl/oracle-free:23-slim` (URL `jdbc:oracle:thin:@localhost:1521/FREEPDB1`) 을 쓴다.
> 다만 **`oracle` 프로파일은 현재 기동 자체가 실패**한다 ([install-issues.md](install-issues.md) #4).

---

## 4. Keycloak 기동 + 필수 설정 (Docker)

### 4-1. 기동

```bash
docker run -d --name uengine-keycloak -p 8280:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -e KC_HTTP_ENABLED=true -e KC_HOSTNAME=http://localhost:8280 \
  -v "$(pwd)/infra/keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro" \
  quay.io/keycloak/keycloak:26.6.1 start-dev --import-realm

until [ "$(curl -s -o /dev/null -w %{http_code} http://localhost:8280/realms/uengine)" = 200 ]; do sleep 3; done
```

> ⚠️ `infra/docker-compose.yml`(구 버전 compose)에 박혀 있는 **`keycloak:18.0.1` 로는 realm import 가 실패**한다
> (`Unable to find composite client role: view-groups`). **26.x 를 쓸 것.**
> 브랜치의 `docker-compose.keycloak-postgres.yml` 은 이미 26.6.1 로 올라가 있다.

### 4-2. HTTP 허용 (필수)

`realm-export.json` 의 `sslRequired: external` 때문에 Docker 를 거친 요청이
`{"error":"invalid_request","error_description":"HTTPS required"}` 로 거부된다.
**`uengine` 과 `master` 두 realm 모두** 풀어야 한다.
(`master` 를 빠뜨리면 process-service 의 Keycloak Admin API 호출이 조용히 실패해서
프로세스 시작이 "not permitted" 로 막힌다.)

```bash
docker exec uengine-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password admin
docker exec uengine-keycloak /opt/keycloak/bin/kcadm.sh update realms/uengine -s sslRequired=NONE
docker exec uengine-keycloak /opt/keycloak/bin/kcadm.sh update realms/master  -s sslRequired=NONE
```

### 4-3. 계정과 역할

이 브랜치의 `realm-export.json` 에는 **`manager` 역할이 이미 포함**되어 있고 `hong` 에게 부여돼 있다.
샘플 BPMN 의 lane 이 `IAMRoleResolutionContext { scope: "manager" }` 를 쓰므로,
**프로세스를 시작하는 사용자는 `manager` 역할을 가져야 한다.**

| 용도 | 계정 | manager 역할 |
|---|---|---|
| Keycloak 관리 콘솔 (master) | `admin` / `admin` | – |
| uengine realm | `hong` / `1234` (email `hong@uengine.org`) | ✅ |
| uengine realm | `admin` / `admin` (email `admin@uengine.org`) | ❌ |
| uengine realm | `유` / `1234` | ❌ |

`admin` 으로 프로세스를 시작하려면 역할을 따로 부여한다:

```bash
docker exec uengine-keycloak /opt/keycloak/bin/kcadm.sh add-roles -r uengine --uusername admin --rolename manager
```

> 공식 문서의 `tester`/`tester` 계정은 `realm-export.json` 에 없다.

---

## 5. 애플리케이션 기동

세 서비스 모두 **`postgres,keycloak-installed`** 프로파일로 띄운다.
(게이트웨이는 `keycloak-installed` 만)

### 5-1. process-service (9294)

```bash
SPRING_PROFILES_ACTIVE=postgres,keycloak-installed \
UENGINE_BASEPATH="$(pwd)" \
KEYCLOAK_URI=http://localhost:8280 \
KEYCLOAK_REALM=uengine \
KEYCLOAK_ADMIN_REALM=master \
KEYCLOAK_ADMIN_CLIENT_ID=admin-cli \
KEYCLOAK_ADMIN_USERNAME=admin \
KEYCLOAK_ADMIN_PASSWORD=admin \
java --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.text=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
     -Dfile.encoding=UTF-8 \
     -jar process-service/target/process-service-1.1-SNAPSHOT.jar \
     --spring.config.additional-location=file:docs/local/process-service.local.yml
```

핵심 포인트 3가지:

1. **`--add-opens` 4개는 필수**다. 없으면 작업 완료 시 XStream 이
   `No converter available ... Unable to make private void java.util.HashMap.readObject accessible` 로 터진다.
   `pom.xml` 의 `spring-boot:run` 설정에는 들어 있지만 `java -jar` 에는 아무도 안 붙여준다
   (`Dockerfile` 도 마찬가지다 — [install-issues.md](install-issues.md) #10).
2. **`KEYCLOAK_REALM=uengine`** 을 반드시 환경변수로 준다. `application.yml` 은 `keycloak.realm` 에
   `KEYCLOAK_ADMIN_REALM`(=master)을 매핑해 두어서, 안 주면 사용자 조회를 master realm 에서 하게 된다.
3. `docs/local/process-service.local.yml` 은 프로파일이 채워주지 않는 것만 보충한다
   (메일 프로퍼티, `basePath`, `metadata_builder_contributor`, 로그 경로).

`mvn spring-boot:run` 으로 띄우면 `--add-opens` 는 자동으로 붙지만, 위의 환경변수는 그대로 필요하다.

### 5-2. definition-service (9293)

```bash
SPRING_PROFILES_ACTIVE=postgres,keycloak-installed \
UENGINE_BASEPATH="$(pwd)" \
PROCESS_SERVICE_URL=http://localhost:9294 \
java -jar definition-service/target/definition-service-1.1-SNAPSHOT.jar \
     --spring.config.additional-location=file:docs/local/definition-service.local.yml
```

> **`PROCESS_SERVICE_URL` 은 반드시 준다.** 정의를 저장할 때 definition-service 가
> process-service 의 `/definition-changes` 를 호출하는데, 기본값이 도커 서비스명
> (`http://process-service:9094`)이라 로컬에서는 502 가 난다.

### 5-3. keycloak-gateway (8288)

```bash
SPRING_PROFILES_ACTIVE=keycloak-installed \
GATEWAY_URI=http://localhost:8288 \
KEYCLOAK_URI=http://localhost:8280 \
KEYCLOAK_REALM=uengine \
KEYCLOAK_CLIENT_ID=uengine \
KEYCLOAK_CLIENT_SECRET=66LpF19OpkpgKKpWHdgiCEKisx5AXqLA \
PROCESS_SERVICE_URI=http://localhost:9294 \
DEFINITION_SERVICE_URI=http://localhost:9293 \
FRONTEND_URI=http://localhost:5373 \
java -jar keycloak-gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

> ⚠️ 프로파일을 **반드시** `keycloak-installed`(또는 `docker`)로 준다.
> 기본 프로파일에는 `spring.security.oauth2` 설정이 없어 **로그인이 아예 붙지 않고** 전부 통과된다.
>
> 브라우저가 보는 주소와 게이트웨이가 서버 간 통신에 쓰는 주소가 다른 경우
> (compose 처럼 Keycloak 이 컨테이너 내부에 있을 때) `KEYCLOAK_INTERNAL_URI` 를 따로 준다.
> 호스트 실행에서는 둘 다 `http://localhost:8280` 이라 생략해도 된다.

---

## 6. 정의 아카이브 생성 (최초 1회, 필수)

`definitions/*.bpmn` 은 `uengine:properties` 안에 자신의 버전을 갖고 있고, 인스턴스 실행 시에는
`archive/<정의경로>/<버전>.bpmn` 을 읽는다. 그런데 **`archive/` 는 `.gitignore` 대상**이라
새로 클론한 저장소에는 존재하지 않는다. 그대로 두면 작업 완료 시 이렇게 실패한다:

```
Error when to load definition: /archive/test/test.bpmn/0.2.bpmn ... (No such file or directory)
```

정상 경로는 **모델러에서 정의를 한 번 저장**하는 것이고, API 로도 만들 수 있다:

```bash
python3 - <<'PY'
import json
xml = open('definitions/test/test.bpmn', encoding='utf-8').read()
json.dump({'definition': xml, 'version': '0.2', 'name': 'test/test'},
          open('/tmp/def.json', 'w'), ensure_ascii=False)
PY
curl -X PUT http://localhost:9293/definition/raw/test/test.bpmn \
     -H 'Content-Type: application/json;charset=UTF-8' --data-binary @/tmp/def.json
```

- 버전 값은 해당 `.bpmn` 의 `&quot;version&quot;:&quot;0.2&quot;` 를 그대로 쓴다 (파일마다 다르다).
- **호출한 프로세스가 참조하는 서브프로세스 버전도 따로** 만들어야 한다.
  예) `test/test` 는 `test/testCall` 의 **1.0** 을 참조하므로 `testCall.bpmn` 도 version `1.0` 으로 저장.
- 경로에 한글이 있으면 URL 인코딩해서 호출한다.

### 6-1. SDS BMT 정의 배포 (이 브랜치 전용, 선택)

`test-assets/sds-export-runnable/` 의 BPMN·Form 을 한 번에 등록한다.
PowerShell 스크립트(`tools/deploy-sds-export.ps1`)와 동일한 동작이다.

```bash
python3 - <<'PY'
import json, os, subprocess, urllib.parse
SRC, DS = "test-assets/sds-export-runnable", "http://localhost:9293"
for name in sorted(os.listdir(SRC)):
    if not name.endswith((".bpmn", ".form")):
        continue
    json.dump({"definition": open(os.path.join(SRC, name), encoding="utf-8").read(),
               "name": os.path.splitext(name)[0]},
              open("/tmp/sds.json", "w"), ensure_ascii=False)
    uri = f"{DS}/definition/raw?defPath={urllib.parse.quote(name, safe='')}"
    subprocess.run(["curl", "-s", "-o", "/dev/null", "-X", "PUT", uri,
                    "-H", "Content-Type: application/json;charset=utf-8",
                    "--data-binary", "@/tmp/sds.json"], check=True)
    print("deployed", name)
PY
```

분기 검증(`tools/test-sds-branches.ps1`)은 PowerShell + compose 전제라 이 문서 범위에서는 실행하지 않았다.

---

## 7. 접속 및 확인

### 7-1. 브라우저 로그인

`http://localhost:8288/` 접속 → Keycloak 로그인 화면 → `hong` / `1234`.

리다이렉트 체인은 다음과 같다:

```
GET  /                                   302 → /oauth2/authorization/keycloak
GET  /oauth2/authorization/keycloak      302 → keycloak .../protocol/openid-connect/auth
POST .../login-actions/authenticate      302 → /login/oauth2/code/keycloak?code=...
GET  /login/oauth2/code/keycloak         302 → /            (SESSION_ID 쿠키 발급)
```

> 프론트엔드(5373)가 없으면 로그인 후 `/` 는 500 이다. 로그인 자체는 정상 완료된 것이고,
> 아래 API 는 문제없이 동작한다.

### 7-2. API 호출 규칙 (중요)

게이트웨이는 **TokenRelay 필터가 없어서** 로그인 세션의 액세스 토큰을 백엔드로 전달하지 않는다.
즉 백엔드가 사용자를 식별하려면 프론트엔드처럼 **호출자가 직접 `Authorization: Bearer` 를 붙여야** 한다.

- 게이트웨이 통과 조건: 로그인 세션 쿠키(`SESSION_ID`)
- 백엔드 사용자 식별 조건: `Authorization: Bearer <access_token>`
- 둘 다 필요하다.

```bash
TOKEN=$(curl -s -d client_id=uengine -d username=hong -d password=1234 \
             -d grant_type=password -d scope=openid \
             http://localhost:8280/realms/uengine/protocol/openid-connect/token \
        | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

### 7-3. 동작 검증 시나리오

한 번에 확인하려면 검증 스크립트를 쓴다. 위 1~6 단계가 끝난 상태에서:

```bash
GATEWAY_URI=http://localhost:8288 KEYCLOAK_URI=http://localhost:8280 \
USER_ENDPOINT=hong@uengine.org \
bash docs/local/verify-install.sh hong 1234
```
```
[1] 게이트웨이 OAuth2 로그인   ✓ 세션 발급
[2] 액세스 토큰 발급           ✓ hong / hong@uengine.org / roles: ['manager','admin','user']
[3] 정의 목록 조회             ✓ HTTP 200
[4] 프로세스 인스턴스 시작     ✓ instanceId=152
[5] 워크리스트 확인            ✓ workItem=152
[6] 작업 완료 (폼 값 포함)     ✓ HTTP 200 — 다음 액티비티 생성됨
```

개별 호출로 확인하려면:

```bash
GW=http://localhost:8288

# (a) 정의 목록
curl -b cookies.txt -H "Authorization: Bearer $TOKEN" $GW/definition/

# (b) 인스턴스 시작  — endpoints 는 JWT 의 email 클레임 값이어야 한다!
curl -b cookies.txt -X POST $GW/instance \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json;charset=UTF-8' \
  -d '{"processDefinitionId":"test/test",
       "roleMappings":[{"name":"신고자","endpoints":["hong@uengine.org"],"resourceNames":["신고자"]},
                       {"name":"관리자","endpoints":["hong@uengine.org"],"resourceNames":["관리자"]}]}'
# → {"name":"test/test__2026-08-19","instanceId":"152","status":"Running", ...}

# (c) 워크리스트
curl -b cookies.txt -H "Authorization: Bearer $TOKEN" $GW/worklist

# (d) 작업 완료 (폼 값 포함)
curl -b cookies.txt -X POST $GW/work-item/152/complete \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json;charset=UTF-8' \
  -d '{"desiredState":"complete",
       "parameterValues":{"신고내용":[{"_type":"org.uengine.contexts.HtmlFormContext",
         "formDefId":"고장내용","filePath":"고장내용.form",
         "valueMap":{"_type":"java.util.HashMap",
                     "고장":{"_type":"java.util.HashMap","고장유형":"hw","고장내용":"테스트"}}}]}}'
# → 200, 워크리스트에서 해당 항목 COMPLETED + 다음 액티비티("고장접수") 신규 생성
```

> **`endpoints` 값 주의**: `SecurityAwareServletFilter` 는 JWT 의 `email` → `preferred_username` → `sub`
> 순으로 사용자 ID 를 잡는다. realm 사용자에 email 이 있으므로 사용자 ID 는 `hong@uengine.org` 다.
> `roleMappings` 에 `hong` 을 넣으면 인스턴스는 생성되지만
> 완료 시 `403 Only the current work item owner can complete this task` 가 난다.

---

## 8. 종료 / 정리

```bash
# 애플리케이션
kill $(lsof -tiTCP:9294 -sTCP:LISTEN) $(lsof -tiTCP:9293 -sTCP:LISTEN) $(lsof -tiTCP:8288 -sTCP:LISTEN)

# 인프라
docker rm -f uengine-keycloak uengine-postgres
# 데이터까지 초기화하려면
docker volume rm uengine-postgres-data
```

---

## 9. 프론트엔드 (process-gpt-vue3-hli)

프론트엔드는 별도 저장소(`sooheon45/process-gpt-vue3-hli`, 브랜치 `bmt/sds-process-test`)이며
`infra/docker-compose.keycloak-postgres.yml` 은 이를 **형제 디렉터리** `../process-gpt-vue3-hli` 에서 빌드한다.

```
uEngine6-jdk21/
process-gpt-vue3-hli/     ← 여기에 클론
```

```bash
cd ..
gh repo clone sooheon45/process-gpt-vue3-hli -- --branch bmt/sds-process-test
cd process-gpt-vue3-hli
npm install --no-audit --no-fund --legacy-peer-deps
```

### 9-1. 권장: 빌드 산출물을 게이트웨이 뒤에 붙인다

**Vite dev 서버(`npm run dev`)로 게이트웨이 뒤에 붙이면 일부 화면이 깨진다.**
`vite.config.ts` 의 dev 프록시가 `/instance` 를 접두어로 잡아 `/instancelist/*` 까지 가로채고,
프록시 타깃이 `9093/9094` 로 하드코딩돼 있어 이 브랜치의 `9293/9294` 와 어긋나기 때문이다
([install-issues.md](install-issues.md) #22). 정적 빌드는 프록시를 타지 않으므로 이 문제가 없다.

```bash
npm run build

# run.sh 가 컨테이너에서 하는 일과 동일하게 런타임 환경변수를 주입
python3 - <<'PY'
p = 'dist/index.html'
s = open(p, encoding='utf-8').read()
inj = '''<script>
window._env_ = {
  VITE_KEYCLOAK_MODE: "installed",
  VITE_KEYCLOAK_URL: "http://localhost:8280",
  VITE_KEYCLOAK_REALM: "uengine",
  VITE_KEYCLOAK_CLIENT_ID: "uengine",
  VITE_GATEWAY_URL: "http://localhost:8288"
};
</script>
'''
if 'window._env_' not in s:
    open(p, 'w', encoding='utf-8').write(s.replace('<head>', '<head>\n' + inj, 1))
PY
```

`dist` 를 **SPA 폴백이 있는** 정적 서버로 5373 에 띄운다.
(저장소의 `server.js` 는 CommonJS 인데 `package.json` 이 `"type": "module"` 이라 그대로는 실행되지 않는다 — #26)

```bash
npx --yes serve -s dist -l 5373      # 또는 SPA 폴백을 지원하는 아무 정적 서버
```

게이트웨이는 `FRONTEND_URI=http://localhost:5373` 으로 이미 5-3 에서 기동돼 있다.
브라우저에서 **`http://localhost:8288`** 으로 접속한다 (5373 직접 접속이 아니다).

### 9-2. 로그인 흐름

```
http://localhost:8288/todolist
  → 게이트웨이 oauth2Login   → Keycloak 로그인 (hong / 1234)
  → 게이트웨이 세션 쿠키 발급 → SPA 로드
  → SPA 의 keycloak-js 가 SSO 로 토큰 취득 (재로그인 없음)
  → localStorage.userName = hong, 이후 API 호출에 Bearer 첨부
```

`realm-export.json` 의 `uengine` 클라이언트 redirect URI 에 `http://localhost:5373/*` 와
`http://localhost:8288/*` 가 모두 등록돼 있어 두 경로 모두 동작한다.

### 9-3. 검증 결과 (2026-08-19)

정상 렌더링 확인: `/todolist`(Task List — 진행중/보류·반려/완료 칸반), `/definition-map`(배포한 SDS BMT 정의 표시),
`/organization`, `/instancelist/running`(화면은 뜸).

프론트엔드 연동에서 확인된 문제는 [install-issues.md](install-issues.md) #22~#27 에 정리했다. 요약:

| 증상 | 원인 |
|---|---|
| `/definitions-tree` 백지 | 프로덕션 빌드에서 `xlsx` 가 external 로 남아 브라우저가 모듈을 못 찾음 (#24) |
| `/instancelist/running` 콘솔 에러 | 게이트웨이에 `/search/**` 라우트가 없어 SPA HTML 이 응답됨 (#23) |
| SSE 연결 실패 | `/events/**` 라우트 없음 + `pg-notify` 기본 비활성 (#25) |
| 아이콘 일부 404 | `/offline/iconify-api/...` 정적 자산 누락 (#27) |

### 9-4. 기본 프로세스 만들고 실행하기 (Playwright 데모)

화면에서 프로세스를 만들고 실행하는 전 과정을 브라우저를 띄워 그대로 재현한다.

```bash
cp docs/local/demo-basic-process.mjs ../process-gpt-vue3-hli/
cd ../process-gpt-vue3-hli
node demo-basic-process.mjs                 # 브라우저가 보이는 headed 모드
HEADLESS=1 node demo-basic-process.mjs      # 화면 없이
```

> 스크립트를 프론트엔드 저장소 안에 두는 이유: `@playwright/test` 를 그 저장소의
> `node_modules` 에서 해석하기 때문이다.

데모가 보여주는 순서 (스크린샷은 `demo-shots/` 에 남는다):

| # | 화면 | 하는 일 |
|---|---|---|
| 1 | `http://localhost:8288` | 게이트웨이 접속 → Keycloak 로그인 (`hong`/`1234`) |
| 2 | 프로세스 체계도 | 등록된 정의 목록 확인 |
| 3 | `/definitions/demo/basic-process` | **없는 경로로 들어가면 기본 템플릿**(Start → User Task → End)이 열린다 |
| 4 | 디자이너 | 연필 = 편집 모드(정의 lock) → User Task 더블클릭 → 이름을 "신청서 작성" 으로 |
| 5 | 디자이너 | 디스켓 = 저장 → "프로세스 저장" 다이얼로그 → 저장 |
| 6 | 정의 상세 | `/definition-map/sub/<경로>` — 시뮬레이션 / 수정 / **실행** |
| 7 | 실행 | 실행 버튼 → 실행기에서 현재 작업 확인 → 완료 |
| 8 | API | 실제 인스턴스 시작 (`POST /instance`) |
| 9 | 업무 목록 | 생성된 작업이 "진행 중" 에 표시 |
| 10 | API | 작업 완료 (`POST /work-item/{id}/complete`) → 인스턴스 Completed |

**직접 손으로 해보려면** 위 3~7 단계를 그대로 따라 하면 된다. 접속 주소는 항상 `http://localhost:8288` 이다.

주의할 점 두 가지 ([install-issues.md](install-issues.md) #28, #29):

- 화면의 **실행(Execute) 버튼은 항상 시뮬레이션**이다(`simulation: true` 고정).
  DB 인스턴스가 필요하면 `POST /instance` 로 시작해야 한다. 데모 8단계가 그것이다.
- 업무 목록에서 **카드를 클릭해 작업 상세를 여는 경로는 기본 템플릿 기준으로 500** 이 난다
  (`GET /work-item/{id}` — Start 이벤트에 이벤트 동기화가 없어 NPE).
  그래서 데모는 완료 처리를 API 로 한다.

### 9-5. 프로젝트 자체 E2E 스모크 테스트

```bash
cd ../process-gpt-vue3-hli
HLI_BASE_URL=http://localhost:8288 HLI_E2E_USERNAME=hong HLI_E2E_PASSWORD=1234 \
HLI_E2E_SCREENSHOT_REVIEW=0 \
npx playwright test playwright/e2e/hli-uengine-smoke.spec.ts --reporter=list
```

23개 화면을 순회하며 스크린샷을 남긴다. 현재는 `/definitions-tree` 에서 #24 로 실패하며,
그 전까지(`/todolist`, `/definition-map`)는 통과한다.

---

## 10. 아직 해결되지 않은 것

- `GET /instance/{id}` 가 500 (`Type definition error: EmailServiceLocalImpl`),
  `GET /versions` 가 500 (`Range [10, 9) out of bounds`) 으로 실패한다.
- 전체 문제 목록과 근본 조치 제안은 [install-issues.md](install-issues.md) 참조.
