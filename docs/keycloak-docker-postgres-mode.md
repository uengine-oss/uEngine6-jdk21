# Keycloak + PostgreSQL Docker Mode

This mode runs Keycloak, PostgreSQL, the gateway, process service, definition service, and the HLI frontend in Docker.

## Ports

| Component | Port |
| --- | --- |
| Keycloak | 8280 |
| Gateway | 8288 |
| Definition service | 9293 |
| Process service | 9294 |
| Frontend | 5373 |
| PostgreSQL | 5632 |

The normal browser entry point is `http://localhost:8288`; the gateway serves the frontend and routes its backend requests. Keycloak is available at `http://localhost:8280`.

## Build and run

Build the two Maven application artifacts first:

```powershell
mvn -pl process-service,definition-service -am package -DskipTests
Set-Location infra
docker compose -f docker-compose.keycloak-postgres.yml up --build
```

The Compose build creates these deployment images:

```text
uengine-process-service:keycloak-postgres
uengine-definition-service:keycloak-postgres
uengine-keycloak-gateway:keycloak-postgres
uengine-hli-frontend:keycloak
```

The frontend build context is the sibling `process-gpt-vue3-hli` directory. PostgreSQL uses the `uengine` database, and the Spring services run with the `postgres,keycloak-installed` profiles.

## SDS 반출 BPMN 등록 및 분기 검증

반출 BPMN 디렉터리를 실행용 정의와 DynamicForm으로 변환한 뒤 등록한다. 외부 호출 프로세스는 테스트 환경에서 즉시 완료 작업으로 대체된다.

```powershell
node tools/prepare-sds-export.mjs 'C:\path\to\exported-bpmn' test-assets\sds-export-runnable
powershell -ExecutionPolicy Bypass -File tools\deploy-sds-export.ps1
powershell -ExecutionPolicy Bypass -File tools\test-sds-branches.ps1
```

마지막 명령은 각 분기 직전 사용자 작업을 완료하면서 폼 값과 동일한 payload를 전달한다. 결과는 `test-assets/sds-export-runnable/branch-test-report.json`에 저장된다.
