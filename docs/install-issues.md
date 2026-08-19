# 설치·기동 문제 관리대장

로컬 설치를 처음부터 끝까지 실행하면서 실제로 막혔던 지점의 기록이다.
"이번에만 우회한 것"과 "코드/설정을 고쳐야 하는 것"을 구분해 두는 것이 이 문서의 목적이다.

- 최초 작성: 2026-08-19 / 최초 검증 커밋: `45d2b6a1` (main)
- 재검증: 2026-08-19 / 브랜치 **`bmt/sds-process-test`** (`dbb05bb6 Prepare SDS BMT backend runtime`)
- 검증 환경: macOS 26 (Apple Silicon), JDK 21, Docker Desktop, PostgreSQL 16, Keycloak 26.x
- 실행 절차 자체는 [local-install-guide.md](local-install-guide.md) 참조

`bmt/sds-process-test` 브랜치는 이 목록의 일부를 이미 해결했다(#3, #7, #9, #14 부분). 아래 표의 "브랜치" 열 참조.

## 요약

| # | 영역 | 증상 | 조치 | 브랜치 | 근본 수정 필요 |
|---|---|---|---|---|---|
| 1 | 빌드 | JDK 17 로 빌드 불가 | JDK 21 설치 | – | – (문서화) |
| 2 | 빌드 | `-DskipTests` 로 빌드 시 테스트 소스 컴파일 에러 | `-Dmaven.test.skip=true` | 그대로 | ✅ 테스트 코드 |
| 3 | 설정 | 실행 가능한 로컬 프로파일이 없음 (dev/qa/prod 전부 JNDI) | `postgres` 프로파일 | **해결** | – |
| 4 | 설정 | `oracle` 프로파일 기동 실패 (JPQL 검증 에러) | postgres 사용 | 그대로 | ✅ 쿼리 수정 |
| 5 | 설정 | `mail.smtp.*` 미정의로 기동 실패 | 기본값 주입 | 그대로 | ✅ 기본값 부여 |
| 6 | 인프라 | Oracle XE 이미지가 Apple Silicon 미지원 | `oracle-free:23-slim` | 그대로 | ⚠️ compose 갱신 |
| 7 | 인증 | Keycloak 18.0.1 에서 realm import 실패 | 26.x 사용 | **신규 compose 해결** | ⚠️ 구 compose |
| 8 | 인증 | `HTTPS required` 로 OIDC/Admin API 차단 | 두 realm `sslRequired=NONE` | 그대로 | ⚠️ 문서화 or export 수정 |
| 9 | 인증 | realm 에 `manager` 역할 없음 → 프로세스 시작 거부 | – | **해결(hong 에 부여)** | – |
| 10 | 실행 | `java -jar` 시 XStream 변환 실패 | `--add-opens` 4종 | 그대로 | ✅ Dockerfile/기동 스크립트 |
| 11 | 인증 | 게이트웨이가 토큰을 백엔드로 전달하지 않음 | 호출자가 Bearer 직접 첨부 | 그대로 | ❓ 설계 확인 필요 |
| 12 | 설정 | `keycloak.realm` 에 admin realm 이 매핑됨 | `KEYCLOAK_REALM` 환경변수 | 그대로 | ✅ 설정 정리 |
| 13 | 실행 | `archive/` 부재로 인스턴스 진행 불가 | 정의 1회 저장 | 그대로 | ⚠️ 부트스트랩 제공 |
| 14 | 실행 | Feign 대상이 도커 호스트명으로 하드코딩 | `PROCESS_SERVICE_URL` | **부분 해결** | ⚠️ definition 클라이언트 |
| 15 | 실행 | 워크아이템 소유자 불일치 403 | endpoint 를 email 로 | 그대로 | – (문서화) |
| 16 | API | `GET /instance/{id}` 500 | 없음 | 그대로 | ✅ 직렬화 수정 |
| 17 | API | `GET /versions` 500 | 없음 | 그대로 | ✅ 인덱스 버그 |
| 18 | 실행 | 기동 직후 inbox 폴링 NPE 1회 | 없음(무해) | 그대로 | ⚠️ 초기화 순서 |
| 19 | 인프라 | Docker 네트워크 풀 고갈로 compose 실패 | 네트워크 정리/단독 실행 | – | – (환경) |
| 20 | 설정 | postgres 프로파일에 `metadata_builder_contributor` 누락 | 오버라이드에서 지정 | 신규 | ✅ 프로파일 보강 |
| 21 | 인프라 | 구/신 compose 불일치 (Keycloak 18 vs 26, 포트 체계) | 신 compose 사용 | 신규 | ⚠️ 정본 정리 |
| 22 | 프론트 | vite dev 프록시가 `/instancelist/*` 를 가로채고 포트가 어긋남 | 정적 빌드 사용 | 신규 | ✅ 프록시 설정 |
| 23 | 게이트웨이 | `/search`·`/inbox`·`/business-rules` 등 라우트 누락 → SPA HTML 응답 | 없음 | 신규 | ✅ 라우트 추가 |
| 24 | 프론트 | 프로덕션 빌드에서 `xlsx` external → 화면 백지 | 없음 | 신규 | ✅ 빌드 설정 |
| 25 | 실행 | SSE `/events/stream` 미가용 | 없음(무해) | 신규 | ⚠️ 설계 확인 |
| 26 | 프론트 | `server.js` 가 CJS/ESM 충돌로 실행 불가 | 다른 정적 서버 | 신규 | ✅ 확장자 변경 |
| 27 | API | 일부 응답에서 Jackson Infinite recursion → 500 | 없음 | 신규 | ✅ 직렬화 수정 |
| 28 | API | 기본 템플릿 인스턴스의 `GET /work-item/{id}` 500 → 작업 상세 못 엶 | API 로 완료 | 신규 | ✅ NPE 방어 |
| 29 | 프론트 | 화면의 "실행" 이 항상 시뮬레이션 (실제 인스턴스 안 생김) | `POST /instance` | 신규 | ❓ 설계 확인 |
| 30 | 프론트 | 업무 목록 "완료됨" 칸반이 비어 있음 | 없음 | 신규 | ⚠️ 조회 조건 |

---

## 1. JDK 21 필요

- **증상**: `mvn install` 이 릴리스 21 을 요구하며 실패. 기본 JDK 는 17.
- **원인**: `pom.xml` `<java.version>21</java.version>`.
- **조치**: `brew install openjdk@21` 후 `JAVA_HOME` 지정.
- **비고**: README 에 JDK 버전 명시가 없다. 매뉴얼에 선행조건으로 넣었다.

## 2. 테스트 소스가 컴파일되지 않음 ✅ 근본 수정 필요

- **증상**: `mvn -DskipTests install` → `process-service` 테스트 컴파일 에러.
  ```
  symbol: variable BulkAssignResultCode  (BulkAssignDtoContractTest)
  required: BulkAssignSearchRequest,String,String,String / found: ... (인자 개수 불일치)
  ```
- **원인**: 본 소스의 시그니처가 바뀌었는데 테스트가 따라가지 못했다. `-DskipTests` 는 테스트를 *실행*만 건너뛰고 컴파일은 한다.
- **임시 조치**: `-Dmaven.test.skip=true`.
- **근본 조치**: `process-service/src/test/java/org/uengine/hwlife/instance/BulkAssignDtoContractTest.java` 등 테스트 갱신. 지금 상태로는 CI 가 돌 수 없다.

## 3. 로컬에서 실행 가능한 프로파일이 없음 — `bmt/sds-process-test` 에서 해결됨

- **증상**: 어떤 프로파일로 띄워도 DataSource 를 JNDI(`java:comp/env/jdbc/uengine`)에서 찾다 실패.
- **원인**: `main` 의 `application.yml` 은 dev/qa/prod 가 전부 WAS 배포 전제. 과거에 있던 `oracle`/`postgres` 프로파일 문서가 커밋 `f2457484` 에서 삭제됐다.
- **main 에서의 우회**: 존재하지 않는 프로파일명을 활성화해 dev 문서를 비활성화하고 `--spring.config.additional-location` 으로 DataSource 를 외부 주입.
- **해결**: 브랜치 `bmt/sds-process-test` 가 process-service/definition-service 양쪽에
  `postgres`(DataSource·PostgreSQLDialect·`default_schema: public`)와
  `keycloak-installed`(포트 9294/9293, Keycloak URL 8280) 프로파일을 추가했다. → main 에도 반영 필요.

## 4. `oracle` 프로파일 기동 실패 ✅ 근본 수정 필요

- **증상**:
  ```
  Could not create query for ProcessInstanceRepositoryOracle.findFilterICanSee(...)
  SemanticException: Cannot compare left expression of type 'java.lang.String'
                     with right expression of type 'java.lang.Integer'
  ```
- **원인**: 엔티티 필드 타입이 바뀌었는데 `@Profile("oracle")` 전용 리포지토리의 JPQL 이 갱신되지 않았다. `ProcessInstanceRepositoryH2`(`@Profile("!oracle")`) 만 유지보수되고 있다.
- **조치**: 로컬 검증은 PostgreSQL(=`!oracle` 경로)로 진행.
- **근본 조치**: `ProcessInstanceRepositoryOracle.findFilterICanSee` 의 비교 대상 타입 정리. **운영이 Oracle 이라면 현재 HEAD 는 Oracle 로 기동되지 않는다** — 우선순위 높음.

## 5. `mail.smtp.*` 프로퍼티 부재로 기동 실패 ✅ 근본 수정 필요

- **증상**: `Could not resolve placeholder 'mail.smtp.host' in value "${mail.smtp.host}"`.
- **원인**: `EMailServerSoapBindingImpl` 이 `mail.smtp.host/port/auth/starttls.enable/ssl.trust/username/password` 를 **기본값 없이** `@Value` 로 요구하는데, 저장소 어느 설정 파일에도 정의가 없다(WAS 외부 설정 전제).
- **임시 조치**: `docs/local/process-service.local.yml` 에 `mail.enabled: false` + 더미 값.
- **근본 조치**: `@Value("${mail.smtp.host:localhost}")` 처럼 기본값을 주거나, `application.yml` 에 `mail:` 블록을 기본 제공.

## 6. Oracle XE 이미지가 Apple Silicon 에서 기동 불가 ⚠️

- **증상**: `oracle/docker-compose.yml` 기동 후 `ORA-00443: background process "PMON" did not start`.
- **원인**: `gvenzl/oracle-xe:21-slim` 은 amd64 전용. arm64 에서 에뮬레이션으로는 기동되지 않는다.
- **조치**: arm64 매니페스트가 있는 `gvenzl/oracle-free:23-slim` 사용. 접속 URL 이 SID 방식이 아니라 서비스명 방식이다 → `jdbc:oracle:thin:@localhost:1521/FREEPDB1`.
- **근본 조치**: `oracle/docker-compose.yml` 과 `oracle/README.md` 에 arm64 대안 명시.

## 7. Keycloak 18.0.1 로는 realm import 실패 ✅ 근본 수정 필요

- **증상**: `ERROR: Failed to import realm: uengine` / `Unable to find composite client role: view-groups`.
- **원인**: `infra/keycloak/realm-export.json` 은 상위 버전 Keycloak 에서 export 된 것이라 18.0.1 의 `realm-management` 클라이언트에 없는 역할을 참조한다. 그런데 `infra/docker-compose.yml` 은 `quay.io/keycloak/keycloak:18.0.1` 로 고정돼 있다.
- **조치**: `quay.io/keycloak/keycloak:26.0` 사용 (import 성공 확인).
- **근본 조치**: compose 의 Keycloak 이미지 버전 상향. `docs/keycloak-installed-mode.md` 는 이미 26.6.1 을 권장하고 있어 문서 간 불일치 상태다. 26 부터 `KEYCLOAK_ADMIN*` → `KC_BOOTSTRAP_ADMIN_*` 로 바뀐 점도 함께 반영 필요.

## 8. `HTTPS required` 로 인증이 전부 막힘 ⚠️

- **증상 A**: 게이트웨이 기동 실패 — `Unable to resolve Configuration with the provided Issuer of "http://localhost:8080/realms/uengine"`.
  실제 원인은 `/.well-known/openid-configuration` 이 `403 {"error_description":"HTTPS required"}`.
- **증상 B**: (uengine realm 만 풀었을 때) 프로세스 시작이
  `You (...) are not permitted to initiate this process` 로 실패.
  process-service 가 **master realm** 에서 admin 토큰을 받아 역할을 조회하는데 그 호출이 막혀서
  `IAMRoleResolutionContext.containsMapping` 이 예외를 삼키고 `false` 를 반환하기 때문이다.
- **원인**: `realm-export.json` 의 `sslRequired: external` + Docker 를 거치며 클라이언트 IP 가 사설망으로 인식되지 않음.
- **조치**: `uengine`, `master` **두 realm 모두** `sslRequired=NONE`.
- **근본 조치**: 로컬용 realm export 를 별도로 두거나 설치 문서에 필수 단계로 명시.
  부가로, `containsMapping` 이 예외를 통째로 삼켜 `false` 를 돌려주는 탓에 원인 파악이 매우 어려웠다 → 최소한 warn 로그는 남길 것.

## 9. realm 에 샘플 프로세스가 요구하는 역할이 없음 — `bmt/sds-process-test` 에서 해결됨

- **증상**: 인스턴스 시작 시 `The initiator group is 'Who has the scope 'manager''`.
- **원인**: `definitions/**` 샘플 BPMN 의 lane 이 `IAMRoleResolutionContext { scope: "manager" }` 를 쓰는데, `main` 의 `realm-export.json` 의 realm 역할은 `user`/`admin`/`process-manager` 뿐이다.
- **해결**: 브랜치의 `realm-export.json` 에 `manager` 역할이 추가되고 **`hong` 사용자**에게 부여됐다.
  아울러 `hong` 에 `realm-management` 클라이언트 역할(`view-realm`/`view-users`/`query-groups`/`query-users`)도 붙었다.
- **주의**: `admin` 사용자에게는 `manager` 가 없다. `admin` 으로 프로세스를 시작하려면 역할을 따로 부여해야 한다.
  → 설치 매뉴얼의 검증 계정을 `hong`/`1234` 로 잡았다.

## 10. `java -jar` 실행 시 XStream 변환 실패 ✅ 근본 수정 필요

- **증상**: 워크아이템 완료 시
  ```
  No converter available / type: org.uengine.kernel.IndexedProcessVariableMap
  Unable to make private void java.util.HashMap.readObject(...) accessible
  ```
- **원인**: JDK 17+ 모듈 캡슐화. `process-service/pom.xml` 의 `spring-boot:run`/`surefire` 설정에는
  `--add-opens java.base/java.util`, `java.base/java.lang.reflect`, `java.base/java.text`,
  `java.desktop/java.awt.font` 가 들어 있으나 **`java -jar` 경로에는 없다.**
- **조치**: 기동 명령에 4개 `--add-opens` 추가 → 완료 성공 확인.
- **근본 조치**: `process-service/Dockerfile` 의 `JAVA_OPTS` 에 동일 옵션 추가.
  현재 Dockerfile 로 만든 이미지도 같은 지점에서 실패할 것으로 보인다(=운영 영향 가능).

## 11. 게이트웨이가 액세스 토큰을 백엔드로 전달하지 않음 ❓

- **증상**: 게이트웨이 로그인 세션만으로 API 를 호출하면 백엔드가 사용자를 식별하지 못한다.
  반대로 Bearer 토큰만 보내면 게이트웨이가 로그인 화면으로 302 리다이렉트한다.
- **원인**: `keycloak-gateway` 라우트에 `TokenRelay` 필터가 없고,
  `SecurityConfiguration` 도 `oauth2Login()` 만 켜고 `oauth2ResourceServer()` 는 켜지 않는다
  (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` 설정은 있으나 사용되지 않음).
- **현재 동작 방식**: 프론트엔드가 keycloak-js 로 직접 토큰을 받아 XHR 에 실어 보내고,
  게이트웨이는 세션 쿠키만 확인한 뒤 그대로 프록시한다. 즉 **세션 쿠키 + Bearer 둘 다** 필요.
- **확인 필요**: 의도된 설계인지. 의도라면 문서화, 아니라면 `TokenRelay` 또는 `oauth2ResourceServer` 추가.

## 12. `keycloak.realm` 에 admin realm 이 매핑되어 있음 ✅ 근본 수정 필요

- **증상**: 환경변수 없이 띄우면 사용자/역할 조회를 `master` realm 에서 하게 된다.
- **원인**: `application.yml` (dev/qa/prod)
  ```yaml
  keycloak:
    url:    ${KEYCLOAK_URI:http://localhost:8080}
    realm:  ${KEYCLOAK_ADMIN_REALM:master}   # ← 사용자 realm 자리에 admin realm 기본값
  ```
  반면 `KeycloakIAMService` 는 `keycloak.realm`(사용자 realm)과 `keycloak.admin.realm`(토큰 발급 realm)을 구분해서 읽는다.
- **조치**: `KEYCLOAK_REALM=uengine` 을 환경변수로 명시.
- **근본 조치**: `keycloak.realm` 기본값을 `uengine` 으로, admin realm 은 `keycloak.admin.realm` 키로 분리.

## 13. `archive/` 부재로 인스턴스가 진행되지 않음 ⚠️

- **증상**: 작업 완료 시
  `Error when to load definition: /archive/test/test.bpmn/0.2.bpmn ... (No such file or directory)`.
- **원인**: 실행 시점의 정의는 `archive/<경로>/<버전>.bpmn` 에서 읽는데(`DefinitionXMLServiceImpl`),
  `archive/` 는 `.gitignore` 대상이라 새 클론에 없다. 버전 값은 각 `.bpmn` 의 `uengine:properties` 안에 있다.
- **조치**: `PUT /definition/raw/<경로>` 로 정의를 1회 저장하면 아카이브가 생성된다.
  **호출되는 서브프로세스의 버전도 별도로** 만들어야 한다(`test/test` → `test/testCall` **1.0**).
- **근본 조치**: 최초 기동 시 `definitions/` 를 스캔해 아카이브를 만드는 부트스트랩을 제공하거나,
  설치 매뉴얼에 "모델러에서 전체 정의 1회 저장" 단계를 명시.

## 14. Feign 대상 호스트가 도커 서비스명으로 하드코딩 — 브랜치에서 부분 해결

- **증상**: 정의 저장 시 502
  `[process-service:/definition-changes 실패] ... POST http://process-service:9094/definition-changes`.
- **원인**: `uengine-five-api` 의 `@FeignClient` 애너테이션에 URL 이 박혀 있다.
  애너테이션 `url` 이 `spring.cloud.openfeign.client.config.<name>.url` 프로퍼티보다 우선하므로
  **설정으로 덮을 수 없다**(`refresh-enabled: true` 를 켜도 마찬가지였다).
  `main` 에서는 `/etc/hosts` 에 `127.0.0.1 process-service definition-service` 를 넣는 것이 유일한 우회였다.
- **부분 해결**: 브랜치가 `InstanceService` 를
  `@FeignClient(name="bpm", url="${PROCESS_SERVICE_URL:http://process-service:9094}")` 로 바꿨다.
  → definition-service 기동 시 `PROCESS_SERVICE_URL=http://localhost:9294` 를 주면 502 가 사라진다.
- **남은 것**: `DefinitionService`(`http://definition-service:9093`), `SemanticEntityService` 는 여전히 하드코딩.
  process-service 는 기본적으로 `DefinitionServiceLocalImpl`(`uengine.definition.service.mode=local`)을 쓰므로
  당장 문제가 드러나지 않을 뿐이다.

## 15. 워크아이템 소유자 불일치로 403

- **증상**: `403 Only the current work item owner can complete this task`.
- **원인**: `roleMappings[].endpoints` 에 Keycloak **username**(`admin`)을 넣었으나,
  `SecurityAwareServletFilter` 는 JWT 의 `email` → `preferred_username` → `sub` 순으로 사용자 ID 를 잡아
  실제 로그인 ID 는 `admin@uengine.org` 였다.
- **조치**: endpoint 를 email 값으로 지정.
- **비고**: 코드 문제라기보다 규칙이다. 다만 realm 사용자에 email 이 있는지 없는지에 따라
  식별자가 바뀌는 구조라 혼동이 크다 → 매뉴얼에 명시했다.

## 16. `GET /instance/{id}` 500 ✅ 근본 수정 필요

```
Type definition error: [simple type, class org.uengine.five.overriding.EmailServiceLocalImpl]
```
- 응답 직렬화 대상에 Spring 빈(`EmailServiceLocalImpl`)이 딸려 들어가 Jackson 이 실패한다.
- 인스턴스 상세 조회가 막히므로 UI 영향이 클 것으로 보인다.

## 17. `GET /versions?defPath=...` 500 ✅ 근본 수정 필요

```
Range [10, 9) out of bounds for length 9
```
- 경로 문자열을 자를 때의 인덱스 계산 오류로 보인다. 버전 목록 조회가 불가능하다.

## 18. 기동 직후 inbox 폴링 NPE ⚠️

```
NullPointerException: Cannot invoke "EventInboxRepository.lockUnprocessed(int)" because "this.repo" is null
```
- 폴링 스케줄러가 리포지토리 주입 완료 전에 첫 tick 을 돈다. 기동 직후 1회만 발생하고 이후 정상.
- 무해하지만 로그가 지저분하고, 기동 실패로 오해하기 쉽다. 초기화 순서 정리 권장.

## 19. Docker 네트워크 주소 풀 고갈

```
failed to create network ...: all predefined address pools have been fully subnetted
```
- compose 가 프로젝트마다 브리지 네트워크를 만들면서 기본 주소 풀이 소진된 상태였다(로컬에 31개).
- `docker network prune` 또는 전용 네트워크 없이 `docker run` 으로 기동.
- 코드 문제 아님. 다만 설치 중 처음 만나는 벽이라 매뉴얼에 대처법을 적어 두었다.

## 20. postgres 프로파일에 `metadata_builder_contributor` 누락 ✅ 근본 수정 필요

- **원인**: 브랜치가 추가한 `postgres` 프로파일에는 DataSource/Dialect/`ddl-auto`/`default_schema` 만 있고
  `hibernate.metadata_builder_contributor: org.uengine.five.config.OracleHibernateMetadataContributor` 가 없다.
  삭제되기 전(`f2457484^`) 구 `postgres` 프로파일에는 "미등록 시
  `/instances/search/findFilterICanSee` 가 500 으로 실패한다"는 주석과 함께 들어 있었다.
- **조치**: `docs/local/process-service.local.yml` 에서 지정.
- **근본 조치**: `postgres` 프로파일에 되살릴 것.

## 21. 구 compose(`infra/docker-compose.yml`)와 신 compose 의 불일치 ⚠️

- 브랜치가 추가한 `infra/docker-compose.keycloak-postgres.yml` 은 Keycloak 26.6.1 / PostgreSQL /
  포트 8280·8288·9293·9294·5373 체계로 정리돼 있다.
- 반면 기존 `infra/docker-compose.yml` 은 Keycloak 18.0.1, Kafka+ZooKeeper, 포트 8088·9093·9094 로 남아 있어
  #7 문제를 그대로 안고 있다. 어느 쪽이 정본인지 정리가 필요하다.

---

# 프론트엔드 연동 (process-gpt-vue3-hli @ bmt/sds-process-test)

검증 방식: 빌드한 SPA 를 5373 에 정적 서빙하고 게이트웨이(8288) 뒤에 붙인 뒤,
Playwright(chromium)로 `hong`/`1234` 로그인 후 화면을 순회했다.

## 22. vite dev 서버로는 일부 화면이 깨진다 ✅ 근본 수정 필요

- **증상**: `npm run dev` 로 띄운 프론트를 게이트웨이 뒤에 붙이면 `/instancelist/running` 이 **HTTP 500 백지**.
- **원인 2가지** (`vite.config.ts` `server.proxy`):
  1. 프록시 키 `'/instance'` 는 **접두어 매칭**이라 `/instancelist/running` 까지 가로챈다.
     SPA 라우트가 백엔드로 프록시돼 index.html 이 서빙되지 않는다.
  2. 프록시 타깃이 `http://localhost:9093/9094` 로 하드코딩돼 있는데,
     이 브랜치의 `keycloak-installed` 프로파일은 **9293/9294** 를 쓴다 → 연결 실패로 500.
- **조치**: 개발용 dev 서버 대신 `npm run build` 산출물을 정적 서빙 (프록시를 타지 않는다).
- **근본 조치**: 프록시 키를 `'^/instance(?:/|$)'` 형태로 좁히고
  (`'^/definition(?:/|$)'` 는 이미 그렇게 돼 있다), 타깃 포트를 env 로 뺄 것.

## 23. 게이트웨이 라우트가 프론트엔드가 쓰는 API 를 다 덮지 못한다 ✅ 근본 수정 필요

게이트웨이(8288)에 로그인 세션 + Bearer 를 붙여 호출한 결과:

| 경로 | 결과 | process-service(9294) 직접 호출 |
|---|---|---|
| `/worklist` | 200 `application/hal+json` ✅ | – |
| `/definition/` | 200 `application/hal+json` ✅ | – |
| `/instances/search/findFilterICanSee` | 200 `application/hal+json` ✅ | – |
| `/search/my-todo` | **200 `text/html`** ❌ | 405 (존재, POST 전용) |
| `/inbox` | **200 `text/html`** ❌ | 405 (존재, POST 전용) |
| `/business-rules` | **200 `text/html`** ❌ | **200** (존재) |
| `/role-assign-rules` | **200 `text/html`** ❌ | 404 |
| `/work-items` | **200 `text/html`** ❌ | 404 |
| `/events/stream` | **200 `text/html`** ❌ | 404 (#25) |

- **원인**: `keycloak-gateway` 의 `instance-servicce` 라우트 predicate 가
  `/worklist, /test, /instance, /work-item, /instances, /dry-run, /validate, /start-and-complete` 뿐이다.
  나머지는 `frontend` 라우트(`/**`)로 떨어져 **SPA 의 index.html 이 응답**된다.
  프론트의 vite dev 프록시에는 이 경로들이 모두 등록돼 있어, dev 모드에서만 우연히 동작한다.
- **실제 영향**: `/instancelist/running` 에서
  `TypeError: Cannot read properties of undefined (reading 'status')` (HTML 을 JSON 으로 파싱).
- **근본 조치**: 게이트웨이 라우트에 `/search/**`, `/inbox/**`, `/business-rules/**`,
  `/role-assign-rules/**`, `/work-items/**`, `/events/**` 추가.
  compose 배포도 같은 설정을 쓰므로 동일하게 깨진다.

## 24. 프로덕션 빌드에서 `xlsx` 모듈 해석 실패 ✅ 근본 수정 필요

- **증상**: `/definitions-tree` 진입 시 화면 백지 +
  `TypeError: Failed to resolve module specifier "xlsx"`.
- **원인**: `vite.config.ts`
  ```ts
  rollupOptions: { external: ['https', 'xlsx'], output: { globals: { 'xlsx': '{}' } } }
  ```
  ES 모듈 출력에서 `external` 은 `import "xlsx"` 를 그대로 남기고, `globals` 는 UMD/IIFE 에만 적용된다.
  → 브라우저가 bare specifier 를 해석하지 못한다. `xlsx` 는 `dependencies` 에 있으므로 external 로 뺄 이유가 없다.
- **영향**: dev 모드에서는 정상(vite 가 해석), **빌드/도커 배포에서만** 깨진다.
- **근본 조치**: `external` 에서 `xlsx` 제거.

## 25. SSE(`/events/stream`)가 동작하지 않는다 ⚠️

- **증상**: 브라우저 콘솔에
  `EventSource's response has a MIME type ("text/html") that is not "text/event-stream"`.
- **원인 2가지**:
  1. `EventStreamController` 가 `@ConditionalOnBean(PgNotifyListener.class)` 인데
     `uengine.messaging.polling.pg-notify.enabled` 기본값이 `false` → 엔드포인트 자체가 없다(404).
  2. 게이트웨이에 `/events/**` 라우트가 없다(#23).
- **영향**: 실시간 갱신 없음(폴링으로 대체). 화면 자체는 뜬다.
- **확인 필요**: 로컬/온프레미스에서 실시간 갱신을 쓸 계획이라면 pg-notify 활성화 + 라우트 추가가 함께 필요하다.

## 26. `server.js` 가 실행되지 않는다 ✅ 근본 수정 필요

```
ReferenceError: require is not defined in ES module scope
```
- `server.js` 는 CommonJS 인데 `package.json` 에 `"type": "module"` 이 있다.
- **조치**: `npx serve -s dist -l 5373` 등 다른 정적 서버 사용.
- **근본 조치**: `server.cjs` 로 이름을 바꾸거나 ESM 문법으로 변경.

## 27. 기타 프론트 연동 관찰

- `GET /offline/iconify-api/mdi.json?icons=...` 404 — 오프라인 아이콘 자산 누락. 아이콘 일부가 안 나온다.
- process-service 로그에 `HttpMessageNotWritableException: Could not write JSON: Infinite recursion (StackOverflowError)`
  가 반복 기록된다. 순환 참조가 있는 응답 객체가 있다. `GET /instance/{id}` 500(#16)과 같은 계열로 보인다.

## 28. 기본 템플릿으로 만든 프로세스의 작업 상세가 열리지 않는다 ✅ 근본 수정 필요

- **증상**: 업무 목록에서 카드를 클릭하면 `/todolist/{id}` 로 이동하지만 화면이 비어 있다.
  ```
  GET /work-item/165 → 500
  Cannot invoke "org.uengine.contexts.EventSynchronization.getMappingContext()"
  because the return value of "org.uengine.kernel.ReceiveActivity.getEventSynchronization()" is null
  ```
- **재현**: 디자이너의 **기본 템플릿**(Start → User Task → End)을 그대로 저장하고 인스턴스를 시작한 뒤
  업무 목록에서 그 작업을 연다. 기존 샘플(`test/test` 등)의 작업은 200 으로 정상이다.
- **원인 추정**: 템플릿의 Start 이벤트가 내부적으로 `ReceiveActivity` 로 매핑되는데
  이벤트 동기화(EventSynchronization)가 없는 상태라 상세 조회 시 NPE 가 난다.
  즉 **가장 기본적인 "새 프로세스 만들어 실행" 경로에서 바로 걸린다.**
- **조치**: 데모/검증에서는 `POST /work-item/{id}/complete` 로 완료 처리.
- **근본 조치**: `getEventSynchronization()` null 방어 또는 템플릿 생성 시 기본 이벤트 동기화 부여.

## 29. 화면의 "실행(Execute)" 은 항상 시뮬레이션이다 ❓ 설계 확인 필요

- **증상**: 디자이너/정의 상세의 실행 버튼을 눌러도 `bpm_procinst` 에 인스턴스가 생기지 않는다.
  실행기 오버레이("Running the process")에서는 작업이 진행되는 것처럼 보인다.
- **원인**: `TestProcess.vue` 의 `startProcess()` 가 `simulation: true` 를 **하드코딩**한다.
  `executeProcess()` 가 `isSimulate='false'` 로 세팅해도 반영되지 않는다.
  실제 실행용 `ProcessExecuteDialog`(역할 매핑 입력 후 `POST /instance`)는
  `SubProcessDetail.vue` 에서 **주석 처리**되고 `dry-run-process` 로 대체돼 있다.
- **영향**: UI 만으로는 실제 인스턴스를 시작할 수 없다. 실제 기동은 API 호출이 필요하다.
- **확인 필요**: 의도된 상태(테스트 전용 빌드)인지, `ProcessExecuteDialog` 를 되살려야 하는지.

## 30. 업무 목록의 "완료됨" 칸반에 완료 작업이 나타나지 않는다 ⚠️

- 작업을 완료하면 "진행 중" 에서는 사라지지만 "완료됨" 칸반은 비어 있다
  (DB 상으로는 `bpm_worklist.status = COMPLETED`, 인스턴스도 `Completed`).
- 완료 목록 조회 조건(기간·범위 필터)을 확인해야 한다. 우선순위는 낮다.

---

## 미검증 영역

- **프론트엔드 전체 화면**: 스모크 테스트 23개 화면 중 `/definitions-tree` 에서 #24 로 중단돼
  그 이후 화면(`/admin`, `/analytics/*`, `/dashboard` 등)은 확인하지 못했다.
- **SDS BMT 분기 테스트**: `tools/test-sds-branches.ps1` 은 PowerShell + `docker compose exec postgres` 전제라
  이번 호스트 실행 구성에서는 돌리지 않았다. 정의 배포(`tools/deploy-sds-export.ps1` 상당)는 77개 파일 등록 성공.
- **Kafka 연동**: 기본 메시징 모드가 `polling` 이라 Kafka 없이 검증했다.
  공식 설치 문서는 Kafka 기동을 필수처럼 안내하고 있어 확인이 필요하다.
- **Oracle/WAS(JNDI) 경로**: 운영 배포 형태인 이 경로는 검증하지 않았다(#4 때문에 현재 기동 불가로 보인다).
