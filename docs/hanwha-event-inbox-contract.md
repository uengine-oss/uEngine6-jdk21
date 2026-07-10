# 한화 비동기 이벤트 연계 계약 — `bpm_event_inbox`

## 개요

한화 측은 우리 BPM 백엔드 API를 직접 호출하지 않고, 공유 DB의 **`bpm_event_inbox` 테이블에 row를 INSERT**하는 방식으로 이벤트를 전달합니다. 우리 엔진이 해당 테이블을 폴링해 사전 등록된 이벤트 매핑(`bpm_event_mapping`)에 따라 프로세스 시작 또는 진행 중 태스크 완료를 수행합니다.

이 문서는 한화 측이 INSERT를 발행할 때 지켜야 할 **컬럼 계약**과 그 의미를 정의합니다.

## 사전 약속 (한·우리 합의 필요)

다음 두 가지는 본 통합을 시작하기 전에 양측 합의가 필요합니다.

1. **업무 이벤트 이름 목록.** 한화 시스템이 발행하는 "사실"의 이름. 예: `심사완료`, `보증인확인완료`. 이름은 한화 도메인의 업무 사실이어야 하며, 우리 내부 식별자(프로세스 ID·태스크 ID 등)는 사용하지 않습니다.
2. **각 이벤트의 상관키 필드명.** 페이로드(JSON) 안에서 어느 필드가 인스턴스를 식별하는 값(예: 신청번호, 보증인 ID 등)을 담고 있는지. 우리 측 매핑 등록 시 이 필드명을 함께 등록합니다.

## 처리 흐름 (요약)

```
한화 시스템                           우리 BPM 시스템
    │                                    │
    │ INSERT INTO bpm_event_inbox        │
    │   (event_name, payload, corr_key)  │
    ├───────────────────────────────────▶│  (① 한화 → DB)
    │                                    │
    │                                    │ ② 폴러가 미처리 row 감지
    │                                    │ ③ event_name 으로 매핑 조회
    │                                    │ ④ 시작 이벤트면 instance start
    │                                    │    태스크 이벤트면 corr_key 로
    │                                    │    인스턴스 찾아 fireReceived
    │                                    │ ⑤ processed_at 갱신
    │                                    │
```

- 한화는 ①만 책임집니다.
- ②~⑤는 우리 엔진 내부 처리이며 한화의 관심사가 아닙니다.

## `bpm_event_inbox` INSERT 컬럼 계약

| 컬럼 | 타입 | 값 / 규칙 | 비고 |
|---|---|---|---|
| `id` | bigint | `nextval('SEQ_BPM_EVENT_INBOX')` | 시퀀스로 발급. 직접 정수 지정 금지. |
| `event_name` | varchar(128) | 합의된 업무 이벤트 이름 | `bpm_event_mapping`에 사전 등록된 값이어야 함. |
| `payload` | text (JSON) | **NOT NULL**, 유효 JSON | 상관키 필드와 그 외 업무 데이터 포함. |
| `corr_key` | varchar(64) | 인스턴스 식별을 위한 업무 키 | NULL 가능 — payload 안의 상관키 필드에서 추출 시. 권장: 명시적으로 채워 보낼 것. |
| `created_at` | timestamptz | `now()` | **NOT NULL** |
| `processed_at` | timestamptz | **NULL 로 INSERT** | 우리 엔진이 처리 완료 시 채움. |
| `try_cnt` | integer | `0` | 처리 시도 횟수. 우리 엔진이 갱신. |
| `status` | varchar(16) | `PENDING` | 우리 엔진이 처리 상태를 갱신. `PENDING` / `SUCCESS` / `FAILED`. |
| `last_error` | text | **NULL 로 INSERT** | 처리 실패 시 우리 엔진이 채움. |

### INSERT 예시 (시작 이벤트)

```sql
INSERT INTO bpm_event_inbox (id, event_name, payload, corr_key, created_at, try_cnt)
VALUES (
  nextval('SEQ_BPM_EVENT_INBOX'),
  '심사요청',
  '{"applicationId":"APP-2026-0042","applicant":"홍길동","amount":50000000}',
  'APP-2026-0042',
  now(),
  0
);
```

### INSERT 예시 (태스크 진행 이벤트)

```sql
INSERT INTO bpm_event_inbox (id, event_name, payload, corr_key, created_at, try_cnt)
VALUES (
  nextval('SEQ_BPM_EVENT_INBOX'),
  '심사완료',
  '{"applicationId":"APP-2026-0042","decision":"approved","reviewer":"심사역A"}',
  'APP-2026-0042',
  now(),
  0
);
```

태스크 진행 이벤트의 `corr_key`는 **시작 이벤트로 만들어진 인스턴스의 `corr_key`와 동일한 값**을 사용해야 합니다. 같은 업무 건은 모든 후속 이벤트에서 같은 corr_key를 사용하는 것이 원칙입니다.

## 멱등성·재시도

- **UNIQUE 제약**: `(corr_key, event_name)`. 같은 (업무키, 이벤트이름) 조합은 한 번만 처리됩니다 — 동일 이벤트를 두 번 INSERT 해도 두 번 실행되지 않습니다. **재전송 안전.**
- **재시도**: 우리 엔진이 처리 중 일시 오류 발생 시 `try_cnt`를 증가시키며 재시도합니다. 일정 횟수 초과 시 `last_error`에 사유가 기록되고 dead-letter 처리됩니다.
- **한화 측 재전송 정책**: 송신 실패 또는 응답 미수신 시 같은 `(corr_key, event_name)`로 재전송하면 중복 실행 위험 없이 안전합니다.

## 결과 가시성

- 처리 결과는 우리 DB(`bpm_procinst`, `bpm_worklist`)에서 조회 가능합니다.
- 별도의 처리 완료 통지 채널(우리 → 한화)은 현재 범위에 포함되지 않습니다.
- `bpm_event_inbox.status`가 `PENDING` → 미처리 또는 재시도 대기, `SUCCESS` → 처리 성공, `FAILED` → dead-letter.
- `processed_at`은 `SUCCESS` 또는 `FAILED`로 최종 처리된 시각이며, `last_error`는 실패 사유입니다.

## 우리 측 권장 (스키마 안정성)

내부 스키마 진화 여지를 확보하기 위해, 향후 한화 전용 **스테이징 테이블 또는 뷰**를 두고 한화 INSERT는 그쪽으로 받은 다음 우리가 `bpm_event_inbox`로 transfer하는 형태도 검토 가능합니다. 초기 도입 단계에서는 본 계약(`bpm_event_inbox` 직접 INSERT)으로 시작하고, 운영 과정에서 필요성 발생 시 도입합니다.

## 멀티 단계 처리 (참고)

업무 흐름이 N개의 단계로 구성되어 단계마다 한화가 이벤트를 발행하는 경우:

- 각 단계는 **서로 다른** `event_name`을 사용합니다 (예: `1차심사완료`, `최종심사완료`).
- 같은 업무 건의 모든 단계는 **동일한 `corr_key`**를 사용합니다.
- 우리 측 프로세스 정의에 각 단계에 대응하는 이벤트 매핑이 사전 등록되어 있어야 합니다.

## 변경 이력

- 2026-05-26: 초안.
