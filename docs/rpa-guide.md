# uEngine RPA 사용·빌드·배포 매뉴얼

이 문서는 uEngine 프로세스의 `RPAActivity`를 사용해 Robot Framework 작업을 실행하는 방법과 서버 워커·클라이언트 에이전트의 빌드 및 운영 배포 방법을 설명합니다.

최종 현행화: 2026-08-20

## 1. 구성과 실행 흐름

uEngine RPA는 프로세스 엔진이 Robot 작업을 직접 실행하지 않고 데이터베이스의 `RPA_JOB` 큐를 통해 에이전트에 전달하는 구조입니다.

```text
BPMN RPAActivity
  → RPA_JOB 생성
    ├─ autoStart=true: QUEUED
    └─ autoStart=false: WAITING → 사용자 실행(trigger) → QUEUED
  → 에이전트가 poll 및 선점(CLAIMED)
  → Robot Framework 실행(RUNNING)
  → 로그·라이브 프레임·영상 업로드
  → 성공(DONE) 또는 실패(FAILED/TIMEOUT) 보고
  → out 파라미터를 ProcessVariable에 반영
  → 프로세스 다음 액티비티 진행
```

실행 모드는 다음 두 가지입니다.

| 모드 | 실행 위치 | 용도 |
|---|---|---|
| `server` | Docker 기반 `rpa-worker` | 서버에서 일괄 실행하는 무인 자동화 |
| `client` | 사용자 PC의 tray agent | 사용자 화면·로컬 프로그램을 이용하는 attended 자동화 |

Job은 여러 에이전트가 동시에 폴링하더라도 DB 조건부 갱신으로 하나의 에이전트만 선점합니다. 한 에이전트 프로세스는 Job을 한 번에 하나씩 순차 실행하며, 서버 처리량은 워커 컨테이너 수를 늘려 확장합니다.

## 2. 주요 디렉터리

| 경로 | 설명 |
|---|---|
| `process-service/src/main/java/org/uengine/five/rpa/` | RPA Activity, Job 엔티티, 큐 서비스와 REST API |
| `rpa-agent/uengine_rpa/runner.py` | server/client 공용 폴링·Robot 실행기 |
| `rpa-agent/uengine_rpa/worker.py` | 서버 워커 진입점 |
| `rpa-agent/uengine_rpa/tray.py` | 클라이언트 tray agent 진입점 |
| `rpa-agent/uengine_rpa/UEngineLibrary.py` | Robot Framework용 uEngine 키워드 라이브러리 |
| `rpa-agent/Dockerfile` | 서버 워커 이미지 정의 |
| `rpa-agent/build-tray.sh` | macOS/Linux 클라이언트 실행파일 빌드·자체 진단 |
| `rpa-agent/build-tray.ps1` | Windows x64 실행파일·ZIP 빌드·자체 진단 |
| `rpa-agent/build-tray-windows.cmd` | PowerShell 실행 정책을 포함한 Windows 빌드 진입점 |
| `.github/workflows/build-rpa-agent-windows.yml` | Windows 에이전트 CI 아티팩트 생성 |
| `sds/SDS_DepositBalanceNotice.bpmn` | server/client 모드가 포함된 예제 프로세스 |

## 3. 사전 요구사항

공통:

- process-service가 실행 중이고 에이전트에서 해당 URL에 접근할 수 있어야 합니다.
- process-service DB에 `RPA_JOB` 테이블이 있어야 합니다.
- RPA Activity가 포함된 프로세스 정의가 배포되어 있어야 합니다.

서버 워커:

- Docker Engine 또는 호환 컨테이너 런타임
- 워커에서 접근 가능한 process-service URL
- 자동화 대상 시스템에 필요한 네트워크 연결

클라이언트 에이전트:

- 실행 PC: Windows 10/11 x64, macOS 또는 Linux 데스크톱의 로그인 세션
- 화면 자동화 시 잠금 해제된 대화형 데스크톱. Windows 서비스의 Session 0에서는 화면 자동화를 실행하지 않습니다.
- 소스 실행·빌드 PC: Python 3.10 이상(Windows 빌드는 Python 3.11 권장)
- 배포된 단일 실행파일을 실행하는 PC에는 Python이 필요하지 않습니다.
- PyInstaller 결과물은 빌드한 OS·CPU 아키텍처 전용이므로 Windows 실행파일은 Windows에서 빌드해야 합니다.

## 4. RPA Activity 작성

### 4.1 Activity 속성

BPMN `serviceTask`의 `uengine:properties` JSON에 다음 타입을 지정합니다.

```json
{
  "_type": "org.uengine.five.rpa.RPAActivity",
  "executionType": "server",
  "robotScript": "*** Settings ***\nLibrary    UEngineLibrary\n...",
  "rpaAuthoringMode": "script",
  "rpaSchemaVersion": 1,
  "rpaSteps": null,
  "autoStart": false,
  "targetUser": null,
  "dockerImage": "uengine/rpa-worker:1.0.0",
  "timeoutSeconds": 600,
  "parameters": []
}
```

| 속성 | 설명 |
|---|---|
| `executionType` | `server` 또는 `client`. 생략하거나 잘못된 값이면 server로 처리 |
| `robotScript` | 실행할 `.robot` 스크립트 원문 |
| `rpaAuthoringMode` | `script`는 Robot 원문 작성, `visual`은 카드형 시각 편집 원본 사용. 기본값 `script` |
| `rpaSchemaVersion` | `rpaSteps` 시각 편집 데이터 계약 버전. 기본값 `1` |
| `rpaSteps` | 시각 편집기의 순서형 작업 JSON. 실제 실행에는 이 데이터로 생성된 `robotScript`를 사용 |
| `autoStart` | `true`면 즉시 `QUEUED`, 기본값 `false`면 `WAITING`으로 생성되어 사용자가 실행할 때 큐잉 |
| `targetUser` | client Job을 가져갈 사용자 식별자. 예: `hong@uengine.org` |
| `dockerImage` | 모델 표시·배포 참고용 메타데이터. Job별 이미지를 자동 실행하거나 선택하지 않음 |
| `timeoutSeconds` | 선점 후 최대 실행 시간. 기본값 600초 |
| `parameters` | 프로세스 변수와 Robot 입출력 이름의 매핑 |

전체 BPMN XML 작성 방식은 `sds/SDS_DepositBalanceNotice.bpmn`의 `일괄 DM 발송`과 `개별 DM 발송` service task를 참고합니다.

### 4.2 입출력 파라미터

```json
{
  "parameters": [
    {
      "argument": {"text": "CUSTOMER_ID"},
      "direction": "in",
      "variable": {"name": "고객ID"}
    },
    {
      "argument": {"text": "RESULT_CODE"},
      "direction": "out",
      "variable": {"name": "처리결과"}
    }
  ]
}
```

- `in`: 프로세스 변수 값을 같은 이름의 Robot 변수로 전달합니다.
- `out`: Robot 결과 JSON의 키를 프로세스 변수에 저장합니다.
- `inout`: 입력 전달과 결과 반영을 모두 수행합니다.
- 명시적인 out 매핑이 하나도 없으면 결과 JSON의 모든 키를 같은 이름의 프로세스 변수로 저장합니다.

입력값은 Robot 실행 시 `--variable NAME:value` 형태로 전달됩니다. 복잡한 객체나 민감정보를 입력값으로 넘기면 DB의 `input_json`에 저장될 수 있으므로 주의합니다.

### 4.3 Robot 스크립트 예제

```robotframework
*** Settings ***
Library    UEngineLibrary

*** Variables ***
${CUSTOMER_ID}    ${EMPTY}

*** Tasks ***
고객 처리
    Log To Console    customer=${CUSTOMER_ID}
    Set Process Output    RESULT_CODE    OK
    Set Process Output    MESSAGE    처리 완료
```

`Set Process Output`으로 기록한 값은 실행 종료 후 process-service에 전달됩니다. 스크립트 종료 코드가 0이면 성공, 그 외에는 실패로 보고되고 RPA Activity가 fault 상태가 됩니다.

프로젝트 제공 주요 키워드:

| Robot 키워드 | 기능 |
|---|---|
| `Set Process Output` | 프로세스에 반환할 결과 키·값 저장 |
| `Get Process Output` | 현재까지 저장한 결과 조회 |
| `Open Browser` / `Go To Url` / `Close Browser` | Playwright 브라우저 제어 |
| `Click Element` / `Input Text` / `Select Option` | selector 기반 브라우저 입력 |
| `Wait For Element` / `Save Element Text As Output` | 요소 대기와 텍스트 결과 저장 |
| `Take Browser Screenshot` | 브라우저 화면 캡처 |
| `Click Screen` / `Type Text` / `Press Key` / `Press Hotkey` | 사용자 데스크톱 입력 자동화 |
| `Take Desktop Screenshot` | 데스크톱 화면 캡처 |
| `Read Text File As Output` / `Write Text File` | 텍스트 파일 입출력 |
| `Copy File` / `Move File` / `List Folder As Output` | 파일·폴더 작업 |
| `Http Get As Output` / `Http Post Json As Output` | HTTP 결과를 프로세스 출력으로 저장 |
| `Json Value As Output` | JSON 경로 값을 프로세스 출력으로 저장 |
| `Open Dm Site` | Playwright로 DM 예제 화면 열기 |
| `Send Dm Via Web` | DM 예제 화면 입력·전송 |
| `Close Dm Site` | 브라우저 종료와 녹화 flush |
| `Send Dm` | 브라우저 없이 DM 예제 API 직접 호출 |

일반 업무 자동화에서는 자체 Robot Library를 이미지 또는 클라이언트 패키지에 추가한 후 `Library`로 불러옵니다.

## 5. 빠른 실행

### 5.1 전체 데모 실행

PostgreSQL, Keycloak, uEngine 서비스, DM 더미 서버와 두 종류의 RPA 에이전트를 한 번에 실행합니다.

```bash
cd direct-run
./run-all.sh
```

접속 정보:

- uEngine: `http://localhost:8088` (`hong` / `1234`)
- DM 데모: `http://localhost:7788`
- process-service: `http://localhost:9094`

상세 데모 순서는 `direct-run/README-RPA-TEST.md`를 참고합니다. 에이전트만 재기동하려면 다음을 실행합니다.

```bash
./direct-run/restart-rpa-agents.sh
```

### 5.2 서버 워커 직접 실행

이미지를 빌드합니다.

```bash
docker build -t uengine/rpa-worker:local ./rpa-agent
```

process-service가 호스트의 9094 포트에서 실행 중인 경우:

```bash
docker run -d \
  --name rpa-worker \
  --restart unless-stopped \
  -e UENGINE_BASE_URL=http://host.docker.internal:9094 \
  -e UENGINE_AGENT_ID=server-worker-01 \
  -e UENGINE_POLL_INTERVAL=3 \
  uengine/rpa-worker:local
```

Linux Docker에서 `host.docker.internal`이 기본 제공되지 않으면 다음 옵션을 추가합니다.

```bash
--add-host=host.docker.internal:host-gateway
```

자동화 대상 URL도 호스트에 있다면 필요한 환경변수를 함께 전달합니다. DM 예제는 다음 값을 사용합니다.

```bash
-e UENGINE_DM_SERVER=http://host.docker.internal:7788
```

로그 확인:

```bash
docker logs -f rpa-worker
```

### 5.3 클라이언트 에이전트를 소스에서 실행

```bash
cd rpa-agent
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-tray.txt

export UENGINE_BASE_URL=http://localhost:9094
export UENGINE_USER=hong@uengine.org
export UENGINE_AGENT_ID=hong-notebook-01
python -m uengine_rpa.tray
```

GUI tray 없이 서비스나 터미널에서 실행할 때:

```bash
UENGINE_TRAY_HEADLESS=1 python -m uengine_rpa.tray
```

환경변수 대신 사용자 홈에 `~/.uengine-rpa-agent.json`을 둘 수 있습니다.

```json
{
  "baseUrl": "https://process.example.com",
  "user": "hong@uengine.org",
  "agentId": "hong-notebook-01"
}
```

환경변수가 설정되어 있으면 설정 파일보다 우선합니다. `targetUser`가 지정된 client Job은 동일한 `UENGINE_USER` 에이전트만 가져갑니다. `targetUser`가 없는 client Job은 어떤 client 에이전트든 가져갈 수 있습니다.

## 6. 빌드

### 6.1 process-service 빌드

RPA 서버 API는 process-service에 포함됩니다.

```bash
mvn -pl process-service -am clean package -Dmaven.test.skip=true
```

생성물은 `process-service/target/` 아래의 실행 JAR입니다. PostgreSQL Docker 이미지를 함께 빌드할 때는 저장소 루트에서 다음을 실행합니다.

```bash
docker build \
  -f process-service/Dockerfile.postgres \
  -t uengine/process-service:postgres .
```

### 6.2 서버 워커 이미지 빌드

```bash
docker build -t registry.example.com/uengine/rpa-worker:1.0.0 ./rpa-agent
docker push registry.example.com/uengine/rpa-worker:1.0.0
```

기본 이미지에는 다음이 포함됩니다.

- Python 3.11
- Robot Framework와 requests
- Xvfb 가상 화면
- ffmpeg 화면 녹화·프레임 캡처
- Playwright Chromium
- 설치 가능한 경우 `rpaframework`

`rpaframework` 설치는 현재 Dockerfile에서 선택 사항이므로 `RPA.*` 라이브러리를 사용하는 운영 이미지라면 Dockerfile에서 필요한 패키지와 버전을 명시적으로 고정하고 빌드 단계에서 import를 검증하는 것을 권장합니다.

### 6.3 client tray 실행파일 빌드

macOS/Linux:

```bash
cd rpa-agent
./build-tray.sh
```

결과물은 `rpa-agent/dist/` 아래에 생성되며 빌드 직후 포함 모듈 자체 진단이 실행됩니다.

Windows x64 빌드 머신에서는 Command Prompt 또는 PowerShell에서 다음을 실행합니다.

```powershell
cd rpa-agent
.\build-tray-windows.cmd
```

생성물:

- `dist\uengine-rpa-agent.exe`: 설치 없이 실행할 Windows tray agent
- `dist\uengine-rpa-agent-windows-x64.zip`: 배포용 압축 파일

PowerShell에서 Python 실행기를 지정해야 하면 다음처럼 직접 호출합니다.

```powershell
.\build-tray.ps1 -Python "C:\Python311\python.exe"
```

Windows PC가 없는 경우 GitHub Actions의 **Build Windows RPA Agent** workflow를 수동 실행하거나 `rpa-agent-v*` 형식의 tag를 push합니다. 완료 후 workflow artifact의 `uengine-rpa-agent-windows-x64`에서 EXE와 ZIP을 받을 수 있습니다.

주의사항:

- PyInstaller는 빌드를 실행한 OS 및 CPU 아키텍처용 결과물을 생성합니다.
- tray 실행파일에는 Playwright Python 코드와 driver가 포함되지만 Chromium 바이너리는 포함하지 않습니다.
- 브라우저 작업이 필요한 경우 agent가 최초 실행 시 Chromium을 사용자 캐시에 설치합니다. Windows 기본 경로는 `%LOCALAPPDATA%\ms-playwright`입니다. 최초 실행 PC가 인터넷에 연결되지 않는 환경이면 사전에 `PLAYWRIGHT_BROWSERS_PATH`에 Chromium을 배포해야 합니다.
- `Send Dm Via Web`은 브라우저 시작 실패 시 DM 예제 API 방식으로 폴백하지만, 일반 브라우저 키워드는 브라우저 설치 실패를 그대로 보고합니다.
- 운영 배포 전 EXE 코드 서명과 SmartScreen 검증, macOS 서명·공증, Linux 데스크톱 의존성을 조직 정책에 맞게 처리합니다.

### 6.4 Windows 설치와 실행

설정 파일 `%USERPROFILE%\.uengine-rpa-agent.json`을 작성합니다.

```powershell
@'
{
  "baseUrl": "https://process.example.com",
  "user": "hong@uengine.org",
  "agentId": "hong-windows-01"
}
'@ | Set-Content -Encoding UTF8 "$HOME\.uengine-rpa-agent.json"
```

ZIP을 원하는 폴더에 풀고 `uengine-rpa-agent.exe`를 실행합니다. 시스템 tray에 uEngine 아이콘이 표시되며 종료는 아이콘 메뉴의 `Quit`을 사용합니다. 화면 자동화가 필요하면 작업 스케줄러를 **사용자가 로그온할 때만 실행**하도록 구성하고 Windows 서비스로 등록하지 않습니다.

## 7. Docker Compose 배포

process-service와 워커가 같은 Compose 네트워크에 있다면 서비스 이름으로 연결합니다.

```yaml
services:
  process-service:
    image: uengine/process-service:postgres
    environment:
      SPRING_PROFILES_ACTIVE: postgres
      POSTGRES_HOST: postgres
      POSTGRES_DB: uengine
      POSTGRES_USER: uengine
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      UENGINE_BASEPATH: /data/uengine
    volumes:
      - process-data:/data/uengine

  rpa-worker:
    image: registry.example.com/uengine/rpa-worker:1.0.0
    restart: unless-stopped
    depends_on:
      - process-service
    environment:
      UENGINE_BASE_URL: http://process-service:9094
      UENGINE_AGENT_ID: server-worker-01
      UENGINE_POLL_INTERVAL: 3
      UENGINE_SCREEN_SIZE: 1280x800

volumes:
  process-data:
```

동시 처리량이 필요하면 서로 다른 `UENGINE_AGENT_ID`로 워커를 여러 개 배포합니다. Compose `--scale`을 사용할 때는 고정 ID를 제거해 워커가 임의 ID를 생성하도록 하거나, 오케스트레이터의 Pod/Task 이름을 ID로 주입합니다.

## 8. Kubernetes 배포 권장사항

서버 워커는 상태 비저장 Deployment로 배포할 수 있습니다.

- `UENGINE_BASE_URL`은 클러스터 내부 process-service 주소로 설정합니다.
- `UENGINE_AGENT_ID`에는 Pod 이름을 Downward API로 주입합니다.
- Job 실행 시간보다 짧은 강제 종료는 피하고 충분한 `terminationGracePeriodSeconds`를 설정합니다.
- 워커 하나가 Job 하나를 순차 처리하므로 replica 수가 최대 동시 실행 수입니다.
- 자동화 대상 시스템의 egress, DNS, 프록시와 인증서 신뢰 설정을 확인합니다.
- 화면 녹화 파일은 워커의 임시 디렉터리에 생성된 후 process-service로 업로드되므로 워커 자체 영구 볼륨은 필수가 아닙니다.

예시 환경변수:

```yaml
env:
  - name: UENGINE_BASE_URL
    value: http://process-service:9094
  - name: UENGINE_AGENT_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: UENGINE_POLL_INTERVAL
    value: "3"
```

## 9. 데이터베이스 준비

`postgres` 프로필은 `spring.jpa.hibernate.ddl-auto=update`이므로 개발 환경에서는 `RPA_JOB` 테이블이 자동 생성됩니다. `qa`의 `validate` 또는 `prod`의 `none` 설정에서는 배포 전에 DBA 마이그레이션이 필요합니다.

PostgreSQL 기준 예시 DDL:

```sql
create table if not exists rpa_job (
    job_id varchar(40) primary key,
    instance_id varchar(255),
    tracing_tag varchar(255),
    definition_id varchar(255),
    activity_name varchar(255),
    mode varchar(255),
    target_user varchar(255),
    status varchar(255),
    agent_id varchar(255),
    script text,
    input_json text,
    result_json text,
    log_text text,
    error text,
    timeout_seconds integer not null default 600,
    created_date timestamp,
    claimed_date timestamp,
    completed_date timestamp
);

create index if not exists idx_rpa_job_poll
    on rpa_job (status, mode, created_date);
create index if not exists idx_rpa_job_client_poll
    on rpa_job (status, mode, target_user, created_date);
create index if not exists idx_rpa_job_instance
    on rpa_job (instance_id, created_date);
```

운영 스키마가 `public`이 아니면 process-service의 `UENGINE_DB_SCHEMA` 및 DDL 실행 스키마를 동일하게 맞춥니다.

## 10. 환경변수

### 공용 runner

| 변수 | 기본값 | 설명 |
|---|---|---|
| `UENGINE_BASE_URL` | `http://localhost:9094` | process-service 기준 URL |
| `UENGINE_AGENT_ID` | 모드별 임의 ID | 로그·Job에 기록할 고유 에이전트 ID |
| `UENGINE_POLL_INTERVAL` | `3` | Job이 없을 때 폴링 간격(초) |

### client tray

| 변수 | 기본값 | 설명 |
|---|---|---|
| `UENGINE_USER` | 없음 | client Job의 `targetUser`와 비교할 사용자 식별자 |
| `UENGINE_TRAY_HEADLESS` | `0` | `1`이면 tray GUI 없이 실행 |
| `PLAYWRIGHT_BROWSERS_PATH` | OS별 사용자 Playwright 캐시 | 사전 배포한 브라우저가 있는 사용자 지정 경로 |

### 화면 자동화와 예제 Library

| 변수 | 기본값 | 설명 |
|---|---|---|
| `UENGINE_SCREEN_SIZE` | `1280x800` | Xvfb와 ffmpeg 캡처 해상도 |
| `UENGINE_RPA_HEADFUL` | 서버 이미지에서 `1` | 브라우저를 실제 화면에 표시 |
| `UENGINE_DM_SERVER` | `http://localhost:7788` | DM 예제 서버 URL |
| `UENGINE_RPA_STEP_DELAY` | 없음 | DM 예제 화면 단계별 대기 시간 |
| `UENGINE_AGENT_LABEL` | 모드·ID로 자동 생성 | DM 예제에 표시할 실행 주체 |

`UENGINE_RPA_OUTPUT`, `UENGINE_RPA_MODE`, `UENGINE_RPA_VIDEO_DIR`, `UENGINE_RPA_FRAME_FILE`은 runner가 Job별로 관리하는 내부 변수이므로 일반 배포에서는 직접 설정하지 않습니다.

## 11. REST API와 상태 확인

| 메서드 | 경로 | 용도 |
|---|---|---|
| `POST` | `/rpa/validate` | Robot 스크립트 문법·키워드 검증 |
| `POST` | `/rpa/poll?agentId=&mode=&user=` | Job 폴링 및 원자적 선점 |
| `POST` | `/rpa/jobs/{jobId}/trigger` | `WAITING` Job을 `QUEUED`로 전환 |
| `POST` | `/rpa/jobs/{jobId}/retry` | `FAILED`·`TIMEOUT` Job을 새 Job으로 재실행 |
| `POST` | `/rpa/jobs/{jobId}/start` | `RUNNING` 전환 |
| `POST` | `/rpa/jobs/{jobId}/log` | 실행 로그 추가 |
| `POST` | `/rpa/jobs/{jobId}/complete` | 성공·실패와 결과 보고 |
| `POST/GET` | `/rpa/jobs/{jobId}/frame` | 최신 JPEG 라이브 프레임 업로드·조회 |
| `POST/GET` | `/rpa/jobs/{jobId}/video` | WebM 실행 영상 업로드·조회 |
| `GET` | `/rpa/jobs?instanceId={id}` | 프로세스 인스턴스별 Job 목록 |
| `GET` | `/rpa/jobs/{jobId}` | Job 상세, 전체 로그와 결과 조회 |

process-service API 확인:

```bash
curl -fsS 'http://localhost:9094/rpa/jobs?instanceId=__healthcheck__'
```

정상이면 빈 배열 `[]`이 반환됩니다. `/rpa/poll`은 실제 Job을 선점할 수 있으므로 헬스체크로 사용하지 않습니다.

서버 워커 확인:

```bash
docker inspect -f '{{.State.Running}}' rpa-worker
docker logs --tail 100 rpa-worker
```

시작 로그에 `started agentId=... mode=server`가 보여야 합니다. client agent는 프로세스와 로그에서 `mode=client`, `user=...`를 확인합니다.

Job 상태는 다음 순서로 전환됩니다. 재실행은 기존 Job을 변경하지 않고 새 Job을 만들며, `autoStart=false`로 생긴 새 `WAITING` Job도 즉시 `QUEUED`로 전환합니다.

```text
autoStart=true  ───────────────→ QUEUED ─→ CLAIMED ─→ RUNNING ─→ DONE
autoStart=false → WAITING ─trigger↗                         ├─→ FAILED ─retry→ 새 QUEUED
                                                           └─→ TIMEOUT ─retry→ 새 QUEUED
```

시간 초과 정리는 다른 에이전트의 `/rpa/poll` 요청 시 수행됩니다. 별도의 heartbeat API는 현재 제공하지 않습니다.

## 12. 로그, 영상과 저장소

- Job 로그는 DB `log_text`에 최대 최근 200KB가 저장됩니다.
- 목록 API는 로그의 마지막 4,000자만 반환하고 상세 API는 전체 저장 로그를 반환합니다.
- 라이브 프레임은 process-service 메모리에만 저장되며 마지막 업로드 후 15초 동안 활성으로 표시됩니다.
- 영상은 `${UENGINE_BASEPATH}/rpa-videos/{jobId}.webm`에 저장됩니다.
- 프레임 최대 크기는 2MB, 서버 수신 영상 최대 크기는 60MB입니다. runner는 50MB를 초과한 영상을 업로드하지 않습니다.

process-service를 여러 replica로 운영할 때 주의합니다.

- 라이브 프레임은 메모리 기반이므로 업로드와 조회 요청이 다른 replica로 가면 보이지 않습니다.
- 영상 파일은 공유 볼륨이 없으면 업로드를 받은 replica에만 존재합니다.
- 라이브 조회에는 sticky session을 적용하거나, 프레임·영상을 공용 객체 저장소로 변경해야 합니다.
- `${UENGINE_BASEPATH}`를 영구 볼륨으로 마운트하지 않으면 컨테이너 교체 시 영상이 사라집니다.

## 13. 보안 및 운영 체크리스트

- 현재 RPA API와 agent 프로토콜에는 별도의 API 토큰 설정이 없습니다. process-service RPA 경로를 인터넷에 직접 노출하지 말고 신뢰된 내부 네트워크로 제한합니다.
- 프록시에서 인증을 강제하면 현재 agent가 Authorization 헤더를 보내지 못하므로 내부 전용 경로 또는 agent 인증 기능 추가가 필요합니다.
- Robot 스크립트는 외부 시스템과 파일에 접근할 수 있는 실행 코드입니다. 프로세스 정의 편집·배포 권한을 최소화합니다.
- 비밀번호·토큰을 Robot 스크립트, Job 입력값 또는 로그에 직접 남기지 않습니다. 컨테이너 Secret, OS 자격증명 저장소 또는 전용 Vault를 사용합니다.
- 운영 이미지는 태그 대신 digest 고정, 취약점 스캔, 의존성 버전 고정과 사내 레지스트리 서명을 적용합니다.
- 운영 배포 전에 timeout, 재실행의 멱등성, 대상 시스템 장애 시 보상 절차를 정의합니다.
- 서버·클라이언트 에이전트에 중복되지 않는 안정적인 `UENGINE_AGENT_ID`를 부여합니다.

## 14. 트러블슈팅

### Job이 계속 QUEUED인 경우

1. 실행 모드와 에이전트 모드가 같은지 확인합니다.
2. client 모드이면 Activity의 `targetUser`와 `UENGINE_USER`가 정확히 같은지 확인합니다.
3. 에이전트의 `UENGINE_BASE_URL`과 process-service 네트워크 접근을 확인합니다.
4. 에이전트 로그에서 `poll failed` 메시지를 확인합니다.

### Job이 WAITING인 경우

- 오류가 아니라 `autoStart=false`의 정상 상태입니다.
- 인스턴스 화면의 `RPA 실행` 버튼을 누르거나 `POST /rpa/jobs/{jobId}/trigger`를 호출합니다.
- 자동 실행이 필요한 모델은 Activity의 `autoStart`를 `true`로 저장합니다.

### CLAIMED 또는 RUNNING에서 멈춘 경우

- 해당 Job의 `agentId`, 상세 로그와 `timeoutSeconds`를 확인합니다.
- 워커가 강제 종료되면 Job은 즉시 재큐잉되지 않습니다. 제한 시간을 넘긴 뒤 다음 poll 요청에서 `TIMEOUT` 처리됩니다.
- 자동 재시도는 없지만 `POST /rpa/jobs/{jobId}/retry`로 `FAILED`·`TIMEOUT` Job을 수동 재실행할 수 있습니다. 대상 작업의 멱등성을 먼저 확인합니다.

### Robot keyword를 찾지 못하는 경우

- 스크립트의 `Library    UEngineLibrary` 선언을 확인합니다.
- 사용자 Library와 Python 패키지가 워커 이미지 또는 client 실행 환경에 포함됐는지 확인합니다.
- Docker 이미지를 다시 빌드한 뒤 실제 배포 태그 또는 digest가 변경됐는지 확인합니다.

### 브라우저가 보이지 않거나 영상이 없는 경우

- 서버 워커의 `DISPLAY`, Xvfb, ffmpeg 프로세스와 `UENGINE_SCREEN_SIZE`를 확인합니다.
- client agent 최초 실행 시 Chromium 설치가 끝났는지, `%LOCALAPPDATA%\ms-playwright` 또는 `PLAYWRIGHT_BROWSERS_PATH`에 쓰기 권한이 있는지 확인합니다.
- process-service의 `UENGINE_BASEPATH` 쓰기 권한과 영구 볼륨을 확인합니다.
- 영상이 50MB를 초과하면 runner가 업로드하지 않습니다.

### Windows agent가 실행되지 않는 경우

- Microsoft Defender SmartScreen이 차단하면 조직에서 서명한 배포본인지 확인하고 보안 담당 정책에 따라 허용합니다.
- tray 아이콘이 숨겨진 아이콘 영역에 있는지 확인합니다.
- 화면 잠금, 로그아웃 또는 RDP 세션 종료 상태에서는 데스크톱 입력 자동화가 정상 동작하지 않을 수 있습니다.
- 설정 파일 JSON과 `baseUrl`, 방화벽·프록시·사내 인증서 신뢰를 확인합니다.
- 배포 전 `uengine-rpa-agent.exe --uengine-self-test`가 종료 코드 0을 반환하는지 확인합니다.

### 결과가 프로세스 변수에 반영되지 않는 경우

- Robot 스크립트가 `Set Process Output`을 호출했는지 확인합니다.
- 결과 키와 Activity `parameters[].argument.text`가 같은지 확인합니다.
- out 변수 이름과 실제 ProcessVariable 이름이 같은지 확인합니다.
- Job 완료 시 Activity 또는 프로세스가 이미 종료되지 않았는지 process-service 로그를 확인합니다.

## 15. 배포 전 최종 확인

- [ ] process-service와 DB가 정상 기동됨
- [ ] 운영 DB에 `RPA_JOB` 테이블과 인덱스가 준비됨
- [ ] 워커에서 process-service 및 자동화 대상에 연결 가능함
- [ ] server/client 모드와 `targetUser`가 올바르게 설정됨
- [ ] Robot Library와 브라우저·OS 의존성이 이미지 또는 client 패키지에 포함됨
- [ ] `UENGINE_BASEPATH/rpa-videos`가 영구 저장됨
- [ ] RPA API가 신뢰된 네트워크로 제한됨
- [ ] 로그와 입력값에 비밀정보가 남지 않음
- [ ] timeout·장애·재실행 시나리오가 검증됨
- [ ] `autoStart` 정책과 `WAITING` 사용자 실행 권한이 정의됨
- [ ] Windows EXE가 Windows runner에서 빌드되고 자체 진단을 통과함
- [ ] Windows EXE 코드 서명·SmartScreen과 최초 Chromium 설치가 검증됨
- [ ] attended 자동화 PC가 로그인·잠금 해제 상태로 운영됨
- [ ] 샘플 프로세스로 결과 변수와 다음 Activity 진행을 확인함
