# JDK 21 + Spring Boot 3.2 변경 사항 정리

## 1. 왜 변경했는지 (배경)

- **JDK 11** → **JDK 21**: LTS 버전 상향, 성능·보안·언어 기능 활용
- **Spring Boot 2.3** → **Spring Boot 3.2**: Java 17+ 기반, Jakarta EE 9+ 전환, Spring Cloud Stream 4 등 최신 스택 적용
- Spring Boot 3.x는 **Java 17 이상**만 지원하므로, JDK 21과 함께 올리는 것이 자연스러운 조합

---

## 2. POM / 의존성 변경

| 구분 | 이전 | 변경 후 | 변경 이유 |
|------|------|---------|-----------|
| **Java** | 11 | **21** | LTS 및 Boot 3.x 요구사항 |
| **Spring Boot** | 2.3.1.RELEASE | **3.2.5** | JDK 21 호환, Jakarta EE 9+ |
| **Spring Cloud** | Hoxton.SR8 | **2023.0.2** | Boot 3.2와 호환되는 BOM |
| **Spring Cloud Stream** | Horsham.SR13 (별도 BOM) | **Spring Cloud BOM에 포함** | Stream 4.x는 Cloud 2023.x에 포함 |
| **Kafka Stream** | spring-cloud-starter-stream-kafka | **spring-cloud-stream-binder-kafka** | Boot 3.2 / Stream 4 표준 아티팩트 |
| **분산 추적** | spring-cloud-starter-sleuth | **micrometer-tracing-bridge-brave** | Sleuth는 Boot 3에서 제거, Micrometer Tracing으로 대체 |
| **JUnit** | junit-vintage-engine (버전 고정) | 부모 관리 버전 사용 | Boot 3.2가 JUnit 5 기본, vintage는 호환용 |

**모듈별**

- **uengine-core**
  - `javax.servlet`, `javax.ejb`, `javax.ws.rs` 제거  
  - **jakarta.servlet-api**, **jakarta.ejb-api**, **jakarta.ws.rs-api** 추가  
  - **HttpClient 4** 제거 → **httpcore5**, **httpclient5** 5.2.1 (Boot 3.2 / Hibernate 6 호환)
- **process-service**
  - spring-security-core 버전 고정 제거 (부모 관리)
  - spring-cloud-stream-test-support → **spring-cloud-stream-test-binder**
  - **spring-boot-starter-mail** (Jakarta Mail)
  - java-jwt 3.x → 4.x

---

## 3. javax → jakarta 전환 (필수)

**이유:** Spring Boot 3.x는 **Jakarta EE 9+**만 지원합니다. `javax.*` 패키지는 더 이상 런타임에 없습니다.

| 패키지 | 변경 | 주로 적용된 모듈/파일 |
|--------|------|------------------------|
| javax.persistence | jakarta.persistence | uengine-five-api 엔티티 전반, JPAProcessInstance, 리소스/모델링 |
| javax.servlet | jakarta.servlet | SecurityAwareServletFilter, InstanceServiceImpl, ScriptActivity, ProcessTransactionContext, UEngineUtil, IntegrationDTO, DefinitionServiceImpl |
| javax.annotation (PostConstruct 등) | jakarta.annotation | DefinitionServiceImpl, FileAuditSink, JPAProcessInstance |
| javax.transaction | jakarta.transaction | JPAProcessInstance, ServiceEndpointEntityListener, DefaultTransactionContext |
| javax.mail / javax.activation | jakarta.mail / jakarta.activation | EMailServerSoapBindingImpl |
| javax.ws.rs.QueryParam | Spring @RequestParam | DefinitionServiceImpl, InstanceServiceImpl (API 호환) |

---

## 4. JDK API 변경

| 구분 | 이전 | 변경 후 | 이유 |
|------|------|---------|------|
| **인증서 API** | javax.security.cert.X509Certificate, CertificateException | **java.security.cert.X509Certificate**, **java.security.cert.CertificateException** | `javax.security.cert`는 JDK에서 제거됨 |

**적용 파일:** `uengine-core/.../ServiceTask.java`

---

## 5. Spring Cloud Stream 3 → 4 (함수형) 전환

**이유:** Stream 4.x에서 `@EnableBinding`, `@Input`, `@Output`, `@StreamListener`가 **제거**되었습니다. 동일 기능을 쓰려면 함수형 모델로 전환해야 합니다.

| 이전 (Stream 3) | 변경 후 (Stream 4) |
|----------------|---------------------|
| `@EnableBinding(Streams.class)` | 제거 |
| `@Input(INPUT)` / `@Output(OUTPUT)` 채널 인터페이스 | **함수형 빈** + yml 바인딩 |
| `@StreamListener(Streams.INPUT)` 다수 메서드 | **단일 Consumer 빈** (`bpm`) + 내부에서 기존 로직 호출 |
| `Streams.outboundChannel()` / `outboundBrodcastChannel()` | **StreamBridge.send("bpm-out", ...)** / **send("bpm-brodcast", ...)** |

**추가/변경된 요소**

- **BpmStreamFunctions**: `Consumer<Message<String>> bpm` 빈 정의
- **BpmMessageDispatcher**: 수신 메시지를 기존 AsyncEventListener / EventListener 로직으로 분기
- **application.yml**: `spring.cloud.function.definition: bpm`, 입력 바인딩 `bpm-in-0` (destination: bpm-topic, group: bpm)
- **Streams**: 인터페이스 → 상수 전용 클래스 (`INPUT`, `OUTPUT`, `OUTPUT_BRODCAST`)
- **StreamsConfig**: `@EnableBinding` 제거

**출력 사용처:** EventListener, ActivityQueue, ActivityCompletionBrodcastListener, JPAProcessInstance, definition-service EventSendingDeployFilter → 모두 StreamBridge 사용

---

## 6. Spring HATEOAS API 변경

**이유:** Boot 3.x와 함께 오는 HATEOAS 2.x에서 `ControllerLinkBuilder`가 제거되고 **WebMvcLinkBuilder**로 통일되었습니다.

| 이전 | 변경 후 |
|------|---------|
| ControllerLinkBuilder.linkTo / methodOn | **WebMvcLinkBuilder.linkTo** / **methodOn** |

**적용 파일:** InstanceResource, DefinitionResource, DefinitionRequest, VersionResource (uengine-five-api, definition-service)

---

## 7. Spring Framework / 기타 API

| 구분 | 이전 | 변경 후 | 이유 |
|------|------|---------|------|
| **ResponseStatusException** | getStatus() | **getStatusCode().value()** | Spring 6에서 반환 타입이 HttpStatusCode로 변경 |
| **Apache HttpClient** | 4.x (org.apache.http.*) | **5.x (org.apache.hc.client5.*, httpcore5)** | Boot 3.2 / RestTemplate가 HttpClient 5 사용 |

**ResponseStatusException 적용:** BusinessRuleStore, ScenarioLoader

**HttpClient 5 적용:** uengine-core ServiceTask (SSL 무검증 RestTemplate 생성 부분)  
- SSL 설정: SSLConnectionSocketFactoryBuilder, TrustAllStrategy, PoolingHttpClientConnectionManagerBuilder 사용

---

## 8. Hibernate 6 (JPA) 변경

**이유:** Spring Boot 3.2는 Hibernate 6.x를 사용합니다. Dialect·함수 등록 API가 변경되었습니다.

| 구분 | 이전 | 변경 후 |
|------|------|---------|
| **Oracle Dialect** | Oracle12cDialect 상속 + registerFunction | **OracleDialect** 상속만 (함수 등록 제거) |
| **커스텀 SQL 함수** | SQLFunctionTemplate, StandardBasicTypes | **PatternBasedSqmFunctionDescriptor** + **PatternRenderer** (MetadataBuilderContributor) |

**적용 파일**

- **Oracle12cDialectWithRegexp**: OracleDialect 상속만 수행. REGEXP 관련은 아래에서 처리.
- **OracleHibernateMetadataContributor**: `regexp_like`, `REGEXP_LIKE_YN`, `regexp_like_yn` 함수를 Hibernate 6 API로 등록

---

## 9. 테스트 변경

| 구분 | 이전 | 변경 후 |
|------|------|---------|
| **AsyncEventListenerTest** | Streams 빈의 inboundGreetings().send(...) | **InputDestination** (test binder) send(..., "bpm-in-0") |
| **EventSynchronizationStreamTest** | TestSupportBinderConfiguration, @EnableBinding(Streams.class) | 제거 (테스트용 채널만 사용) |

InputDestination은 spring-cloud-stream-test-binder 사용 시 제공되며, 바인딩 이름 `bpm-in-0`는 함수형 입력과 일치합니다.

---

## 10. 요약 표

| 영역 | 변경 요약 | 이유 |
|------|-----------|------|
| **JDK** | 11 → 21 | LTS, Boot 3.x 요구 |
| **Boot / Cloud** | 2.3 + Hoxton → 3.2 + 2023 | Jakarta EE, Stream 4, 최신 스택 |
| **javax** | 전부 jakarta 또는 표준 API로 교체 | Boot 3.x는 Jakarta EE 9+ 전용 |
| **Sleuth** | Micrometer Tracing (Brave) | Sleuth 미지원 |
| **Stream** | @EnableBinding/@StreamListener 제거, Consumer + StreamBridge + yml | Stream 4 API 제거 |
| **HATEOAS** | ControllerLinkBuilder → WebMvcLinkBuilder | HATEOAS 2.x |
| **HttpClient** | 4 → 5 (ServiceTask 등) | Boot 3.2와 호환 |
| **Hibernate** | Dialect/함수 등록을 6 API로 이전 | Boot 3.2가 Hibernate 6 사용 |
| **ResponseStatusException** | getStatus() → getStatusCode().value() | Spring 6 API 변경 |

---

## 11. 빌드 및 실행

- **JDK 21** 필요 (예: Temurin 21, jenv global 21 등).
- 빌드: `mvn clean install -DskipTests` (테스트 일부는 기존부터 실패 가능).
- Oracle 사용 시: `SPRING_PROFILES_ACTIVE=oracle` 및 해당 datasource 설정.

이 문서는 JDK 21 + Spring Boot 3.2 전환 시 **무엇을, 왜 바꿨는지**를 정리한 것입니다.
