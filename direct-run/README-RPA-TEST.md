# SDS 예금잔액 통보 (DM/RPA) 직접 테스트 가이드

BPM 프로세스 실행 중 RPA Activity 가 서버(도커)/클라이언트(트레이) 에이전트로
DM 발송을 자동 수행하고, 결과가 프로세스 변수로 매핑되는 것을 직접 확인하는 절차입니다.

## 1. 전체 기동

```bash
cd direct-run
./run-all.sh        # infra(postgres/keycloak) → definition(9093) → process(9094)
                    # → frontend(5173) → gateway(8088) → DM 더미(7788) → RPA 에이전트 2종
```

개별 재기동이 필요하면:

| 스크립트 | 대상 |
|---|---|
| `restart-dm-dummy.sh` | DM 더미 페이지 (7788) |
| `restart-rpa-agents.sh` | 서버 도커 워커 `rpa-worker` + 클라이언트 트레이 에이전트 |
| `restart-process.sh` | process-service (9094) |

## 2. 접속 정보

| 항목 | 값 |
|---|---|
| 프론트엔드 | http://localhost:8088 (게이트웨이 경유) 또는 http://localhost:5173 |
| 로그인 | `hong` / `1234` |
| **DM 발송 더미 페이지** | **http://localhost:7788** ← RPA가 실제로 DM을 쏘는 화면 (1.5초 자동 갱신) |
| process-service API | http://localhost:9094 |

## 3. 프론트에서 실행하기

1. 로그인 후 **체계도**에서 `SDS 뱅킹 > DM 통보 > 예금잔액 통보 (DM/RPA)` 선택
   (정의 트리에서 `SDS_DepositBalanceNotice` 를 직접 열어도 됩니다)
2. 인스턴스 시작 → 첫 작업 **예금잔액 통보 대상 게시** 가 hong 의 할일로 들어옵니다.
3. 폼에서 **본부 일괄발송 예금계좌** 값으로 분기가 결정됩니다:
   - **`Y`** → **일괄 DM 발송** = 서버 사이드 RPA (도커 컨테이너 `rpa-worker`, Robot Framework) — 고객1~5에게 DM 5건
   - **`N`** → **개별 DM 발송** = 클라이언트 사이드 RPA (PC 상주 트레이 에이전트) — DM 1건
4. 작업 완료 직후 **http://localhost:7788** 을 보고 있으면 RPA가 발송하는 DM이
   실시간으로 테이블에 나타납니다 (agent 컬럼에 `server:server-docker-1` / `client:client-tray-1` 표기).
5. RPA 완료 후 **DM 수령 및 확인** 작업이 hong 에게 배정됩니다.
   **예금잔액 이상** = `N` 이면 종료, `Y` 면 **예금잔액 이상 통보** 후 종료.

### 결과 확인 포인트

- 인스턴스 목록/칸반: 실행 중(IN_PROGRESS) → 완료(COMPLETED) 전환
- 프로세스 변수(RPA out-param 매핑):
  - Y 분기: `일괄DM발송건수 = 5`, `DM 반송여부 = N`
  - N 분기: `개별DM발송건수 = 1`, `DM 반송여부 = N`
- RPA 잡 상태 API: `GET /rpa/jobs?instanceId={id}` (QUEUED → RUNNING → DONE, agentId 포함)

## 4. REST 로 자동 실행 (프론트 없이)

```bash
python3 direct-run/e2e_dm_demo.py Y   # 일괄 분기: 서버 도커 RPA, DM 5건
python3 direct-run/e2e_dm_demo.py N   # 개별 분기: 클라이언트 트레이 RPA, DM 1건
```

스크립트가 인스턴스 시작 → 인간 작업 자동 완료 → RPA 잡 폴링 → 최종 변수/DM 목록까지 출력합니다.

## 5. 구성 요소 / 로그

| 구성 요소 | 실행 형태 | 로그 |
|---|---|---|
| 서버 사이드 RPA | 도커 컨테이너 `rpa-worker` (`uengine/rpa-worker` 이미지) | `docker logs -f rpa-worker` |
| 클라이언트 사이드 RPA | `rpa-agent/.venv-build` 트레이 에이전트 (headless 폴백) | `direct-run/logs/rpa-client.log` |
| DM 더미 서버 | `rpa-agent/dm-dummy/dm_server.py` (표준 라이브러리만 사용) | `direct-run/logs/dm-dummy.log` |
| 로봇 스크립트 | BPMN 내 serviceTask `robotScript` 속성 (Robot Framework) | RPA 잡 로그 (`GET /rpa/jobs/{jobId}`) |

DM 더미 페이지 데이터 초기화: 페이지 우측 상단 **내역 초기화** 버튼 또는 `curl -X POST http://localhost:7788/api/clear`

## 6. 트러블슈팅

- **DM이 안 올라옴**: 에이전트 생존 확인 → `docker ps | grep rpa-worker`, `pgrep -f uengine_rpa.tray`.
  둘 다 `./restart-rpa-agents.sh` 로 재기동.
- **작업이 할일에 안 보임**: 프로세스의 모든 레인(고객/본부/영업점)은 데모용으로
  `hong@uengine.org` 에게 직접 배정되도록 설정되어 있음 — hong 계정으로 로그인했는지 확인.
- **BPMN 수정 후**: process-service 재시작 불필요 (정의 캐시 없음). 다음 인스턴스부터 반영.
- **도커 워커가 로봇 키워드를 못 찾음**: `Send Dm` 등 라이브러리 변경 시 이미지 재빌드 필요
  → `cd rpa-agent && docker build -t uengine/rpa-worker .`
