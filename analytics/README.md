# uEngine Analytics ETL

## 분석 결과

`process-gpt-analytic/backend/app/etl.py`는 PostgreSQL의 동일 데이터베이스 안에서 OLTP 테이블을 읽어 `dw` 스타 스키마에 적재합니다. 실행 순서는 스키마 생성, 날짜/시간 차원 생성, 업무 차원 적재, 프로세스/태스크 Fact 적재, 대기시간 후처리입니다. 문서에는 증분 적재라고 되어 있지만 현재 코드는 매 실행마다 원본 전체를 읽고 자연키 기준 upsert하는 스냅샷 마이크로배치입니다.

이 프로젝트는 원본과 실행 환경이 다릅니다.

| 항목 | process-gpt-analytic | 현재 프로젝트 |
|---|---|---|
| 프로세스 원본 | `bpm_proc_inst`의 Supabase 컬럼 | PostgreSQL `bpm_procinst` 테이블 |
| 태스크 원본 | `todolist` | `BPM_WORKLIST` |
| DB | PostgreSQL 전용 SQL | Oracle, PostgreSQL, H2 |
| 실행기 | Python/FastAPI | Spring Boot/JDBC + JPA |

따라서 PostgreSQL 원본 테이블을 JDBC로 읽고 분석 Fact를 JPA로 upsert하도록 구현했습니다. 같은 자연키(`process_instance_id`, `task_id`)를 다시 저장하므로 반복 실행해도 Fact가 중복되지 않습니다.

분석 기능은 루트의 `analytics/` 독립 Maven 하위 프로젝트입니다. process-service와 같은 PostgreSQL을 읽지만 별도 프로세스와 9095 포트에서 실행되므로 process-service 배포 주기와 분리할 수 있습니다.

## 출력 스키마

- `BPM_DIM_DATE`: 업무 타임존 기준 날짜
- `BPM_DIM_PROCESS_DEF`: 프로세스 정의/버전
- `BPM_DIM_ACTIVITY`: tracing tag 기준 액티비티
- `BPM_DIM_ACTOR`: endpoint/group/role 기준 담당자
- `BPM_FACT_PROC_INST`: 프로세스 기간, 상태, 태스크 집계
- `BPM_FACT_TASK`: 처리시간, 이전 태스크 대기시간, 프로세스 lead time, 담당자/활동 키

사람 태스크는 `endpoint` 또는 `resName`이 있는 태스크입니다. 담당자가 없고 `tool` 또는 `actType`이 있는 태스크는 자동화 태스크로 분류합니다. `RETURN` 또는 `BACKTOHERE` 결정은 재작업으로 집계합니다. 음수 시간은 데이터 시각 역전으로 간주해 0으로 보정합니다.

## 실행

개발 프로필의 `ddl-auto: update`는 테이블을 자동 생성합니다. QA/운영은 먼저 DB에 맞는 migration을 적용해야 합니다.

- PostgreSQL: `infra/postgres/migration/2026-08-20-analytics-etl.sql`
- Oracle: `infra/oracle/migration/2026-08-20-analytics-etl.sql`

마이그레이션은 애플리케이션의 기본 스키마(`UENGINE_DB_SCHEMA`) 사용자로 실행합니다.

수동 실행과 상태 확인:

```bash
curl -X POST http://localhost:9095/api/analytics/etl/run
curl http://localhost:9095/api/analytics/etl/status
```

분석 대시보드 조회:

```bash
curl "http://localhost:9095/api/analytics/dashboard?from=2026-08-01&to=2026-08-31"
```

주기 실행은 기본적으로 꺼져 있습니다. 다음 환경 변수로 활성화합니다.

```bash
UENGINE_ANALYTICS_ETL_ENABLED=true
UENGINE_ANALYTICS_ETL_INTERVAL_MS=60000
UENGINE_ANALYTICS_ETL_INITIAL_DELAY_MS=30000
UENGINE_ANALYTICS_ETL_TIME_ZONE=Asia/Seoul
```

## PostgreSQL Docker 실행

저장소 루트에서 PostgreSQL, process-service, analytics, back-office 분석 화면을 함께 실행합니다.

```bash
docker compose -f infra/docker-compose.analytics.yml up --build
```

- 분석 화면: `http://localhost:8082/admin/#/analytics`
- 분석 API: `http://localhost:9095/api/analytics/dashboard`
- 게이트웨이 API: `http://localhost:8088/api/analytics/dashboard`
- PostgreSQL: `localhost:5432` (`uengine` / `uengine`)

analytics 이미지만 만들 때는 저장소 루트를 build context로 사용합니다.

```bash
docker build -t uengine-analytics ./analytics
```

분석 마이그레이션은 PostgreSQL 데이터 볼륨을 처음 만들 때 적용됩니다. 기존 볼륨은 애플리케이션의
analytics 서비스의 `spring.jpa.hibernate.ddl-auto=update`가 누락 테이블을 생성하며, 운영 환경에서는 migration SQL을 별도로 적용합니다.

## 로컬 빌드와 실행

```bash
mvn -pl analytics -am package -Dmaven.test.skip=true

POSTGRES_HOST=localhost \
POSTGRES_DB=uengine \
POSTGRES_USER=uengine \
POSTGRES_PASSWORD=uengine \
java -jar analytics/target/uengine-analytics-1.1-SNAPSHOT.jar
```

Keycloak Gateway는 `keycloak-gateway/src/main/resources/application.yml`의 `/api/analytics/**` 라우트를 사용합니다. 배포 환경에서는 다음 주소를 분석 서비스의 내부 URL로 설정합니다.

```bash
ANALYTICS_SERVICE_URI=http://analytics:9095
```

예시 분석 SQL:

```sql
SELECT pd.definition_name,
       COUNT(*) AS process_count,
       AVG(f.duration_seconds) AS average_duration_seconds,
       SUM(f.rework_task_count) AS rework_tasks
FROM bpm_fact_proc_inst f
JOIN bpm_dim_process_def pd ON pd.process_key = f.process_key
GROUP BY pd.definition_name
ORDER BY process_count DESC;
```

현재 적재 방식은 원본 전체를 읽되 대상 행은 자연키로 갱신합니다. 데이터가 수백만 건 이상이면 `modDate`/변경 이벤트를 신뢰할 수 있게 만든 뒤 고수위표시(high-water mark) 방식으로 전환하는 것이 다음 단계입니다.
