# SQLTask — 데이터베이스에 직접 접근하는 BPMN Task

프로세스 도중 설정된 데이터베이스에 접속하여 SQL 을 바로 실행하는 태스크입니다.
uEngine 3 의 `SQLActivity` / `DatabaseMappingActivity` 를 uEngine 6 커널로 되살린 것으로,
**두 개의 액티비티 타입이 아니라 하나의 `SQLTask` 와 두 개의 strategy** 로 정리했습니다.

| 옛 클래스 | 지금 |
|---|---|
| `org.uengine.kernel.SQLActivity` | `org.uengine.kernel.bpmn.SQLTask` + `DirectSQLStrategy` (기본) |
| `org.uengine.kernel.DatabaseMappingActivity` | `org.uengine.kernel.bpmn.SQLTask` + `DatabaseMappingStrategy` |

## 구성

```
org.uengine.kernel.bpmn.SQLTask                     실행/파라미터 바인딩/결과 매핑 (공통)
 ├─ connectionFactory : org.uengine.util.dao.ConnectionFactory
 │    ├─ DataSourceConnectionFactory   서버에 떠 있는 DataSource 빈(또는 JNDI) 사용
 │    └─ JDBCConnectionFactory         드라이버/URL/계정 직접 지정
 └─ strategy : org.uengine.kernel.bpmn.sql.SQLTaskStrategy
      ├─ DirectSQLStrategy             모델러가 SQL 을 직접 작성 (strategy 미지정 시 기본값)
      └─ DatabaseMappingStrategy       TABLE.COLUMN 매핑 + queryMode 로 SQL 자동 생성
```

strategy 는 "어떤 SQL 을 만들 것인가" 만 결정하고,
커넥션 획득 · `?` 파라미터 바인딩 · 타입 변환 · 결과 → 프로세스 변수 매핑은
`SQLTask` 한 곳에서 처리하므로 두 설정 방식의 실행 의미가 완전히 동일합니다.

## 모델링

BPMN 상에서는 `<bpmn:serviceTask/>` (또는 `<bpmn:task/>`) 로 그리고,
`uengine:properties` JSON 에 `_type` 으로 SQLTask 임을 선언합니다.
back-office 모델러에서는 Task 심볼을 클릭한 뒤 **SQL Task** 로 타입을 바꾸면
전용 속성 패널(연결 방식 / 설정 방식 / SQL / 매핑)이 열립니다.

### 1) SQL 직접 작성

```json
{
  "_type": "org.uengine.kernel.bpmn.SQLTask",
  "connectionFactory": {
    "_type": "org.uengine.util.dao.DataSourceConnectionFactory",
    "dataSourceName": ""
  },
  "sqlStmt": "insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, ?, ?)",
  "parameters": [
    {"argument": {"text": "ID"},           "variable": {"name": "customerId"}},
    {"argument": {"text": "NAME"},         "variable": {"name": "customerName"}},
    {"argument": {"text": "CREDIT_LIMIT"}, "variable": {"name": "creditLimit"}}
  ]
}
```

* `parameters` 는 SQL 의 `?` 순서와 1:1 로 대응합니다 (개수가 다르면 `validate()` 가 지적).
* `sqlStmt` 안에서 `<%변수명%>` 템플릿을 쓰면 실행 시점에 인스턴스 값으로 치환됩니다.
  값 바인딩은 `?` + `parameters` 를 쓰는 편이 안전합니다.
* 조회문이면 `"query": true` 로 두고 `selectMappings` 에 `조회 컬럼명 → 프로세스 변수` 를 적습니다.
* 여러 행이 조회되면 대상 변수는 multiple 프로세스 변수(`ProcessVariableValue`)가 됩니다.
  한 건만 쓰려면 `"applySingleValueOnly": true`.

### 2) 데이터베이스 매핑 (SQL 자동 생성)

```json
{
  "_type": "org.uengine.kernel.bpmn.SQLTask",
  "connectionFactory": {"_type": "org.uengine.util.dao.DataSourceConnectionFactory"},
  "strategy": {
    "_type": "org.uengine.kernel.bpmn.sql.DatabaseMappingStrategy",
    "queryMode": "SELECT",
    "mappingContext": {
      "mappingElements": [
        {"argument": {"text": "CUSTOMER.ID"},           "variable": {"name": "customerId"}, "isKey": true},
        {"argument": {"text": "CUSTOMER.NAME"},         "variable": {"name": "customerName"}},
        {"argument": {"text": "CUSTOMER.CREDIT_LIMIT"}, "variable": {"name": "creditLimit"}}
      ]
    }
  }
}
```

이 `mappingContext` 는 **UserTask 의 폼 매핑과 완전히 같은 구조**다.
모델러에서도 같은 매퍼(`designer/mapper/Mapper.vue`)로 편집한다 —
대상 테이블을 고르면 매퍼 오른쪽 트리에 **DB 에서 읽어온 실제 컬럼**이 뜨고,
왼쪽 프로세스 변수(및 lanes / instance / activities)에서 선을 그으면 `mappingElements` 가 만들어진다.
DatabaseMapping 에서만 추가로 쓰는 것은 `isKey` 한 가지이고, 이것도 **DB 의 기본키를 자동으로 표시**한다.

> uEngine 3 `DatabaseMappingActivity` 편집기의 `TableName` + `[Tables]` + `[refresh]` 와 같은 방식이다.
> (참고: [프로세스 모델링 - DBActivity1/2](https://www.youtube.com/watch?v=idOH92OQUMM))

`queryMode` 별 생성 결과 (키 컬럼은 WHERE 절로, 나머지는 대상 컬럼으로):

| queryMode | 생성되는 SQL |
|---|---|
| `SELECT` | `select NAME, CREDIT_LIMIT from CUSTOMER where ID = ?` |
| `INSERT` | `insert into CUSTOMER (ID, NAME, CREDIT_LIMIT) values (?, ?, ?)` |
| `UPDATE` | `update CUSTOMER set NAME = ?, CREDIT_LIMIT = ? where ID = ?` |
| `DELETE` | `delete from CUSTOMER where ID = ?` |

옛 정의 호환을 위해 `queryMode` 에 정수(`1`=SELECT, `2`=INSERT, `3`=UPDATE, `4`=DELETE)도 받습니다.

## 접속할 데이터베이스 설정

**DataSourceConnectionFactory** — 운영 권장.
`dataSourceName` 이 비어 있으면 애플리케이션의 기본 DataSource 를,
이름을 주면 같은 이름의 Spring `DataSource` 빈(없으면 JNDI)을 찾습니다.
커넥션은 `DataSourceUtils` 로 빌리므로 진행 중인 Spring 트랜잭션에 참여합니다.

**JDBCConnectionFactory** — 외부 DB 를 즉석에서 붙일 때.
`driverClass` / `connectionString` / `userId` / `password` 를 직접 지정합니다.
각 값에 `${키}` 를 쓰면 서버 설정값(`GlobalContext.getPropertyString`)으로 치환되므로
비밀번호를 BPMN 파일에 남기지 않을 수 있습니다.

```json
{
  "_type": "org.uengine.util.dao.JDBCConnectionFactory",
  "driverClass": "org.postgresql.Driver",
  "connectionString": "${crm.jdbc.url}",
  "userId": "${crm.jdbc.user}",
  "password": "${crm.jdbc.password}"
}
```

## 모델러에서 설정하기

실제 프론트엔드(`process-gpt-vue3-hli`)의 bpmn-js 모델러에서는 `bpmn:serviceTask` 를 그린 뒤
속성 패널 맨 위의 **구현 방식** 을 `SQL 실행 (SQLTask)` 으로 바꾼다.

* **연결 방식** — 서버에 설정된 DataSource / JDBC 직접 접속
* **설정 방식** — SQL 직접 작성 / 데이터베이스 매핑
* 데이터베이스 매핑에서는 **대상 테이블을 목록에서 고르고**(연결 설정 기준으로 DB 에서 읽어온다),
  `컬럼 매핑 편집 (Data Mapper)` 로 UserTask 와 같은 매퍼를 열어 프로세스 변수 ↔ 컬럼을 잇는다.
* 컬럼과 **기본키는 DB 메타데이터에서 자동**으로 채워지고, 기본키는 키 컬럼(WHERE 절)으로 미리 체크된다.
  필요하면 매핑 결과 목록에서 직접 바꿀 수 있다.
* 매핑 아래에 **생성될 SQL 미리보기**가 서버 생성기와 같은 규칙으로 표시된다.

### 모델링용 DB 메타데이터 API

`process-service` 가 제공한다 (읽기 전용 — `DatabaseMetaData` 만 읽고, 모델러가 준 SQL 은 실행하지 않는다).

```
GET /sql-metadata/tables?dataSourceName=
GET /sql-metadata/columns?table=CUSTOMER&dataSourceName=
    → {"table":"customer","columns":[{"name":"id","type":"varchar","nullable":false,"primaryKey":true}, ...]}
```

JDBC 직접 접속을 쓰는 경우 `driverClass` / `connectionString` / `userId` / `password` 를 같이 넘긴다.

관련 파일:
* 프론트엔드 — `src/components/designer/bpmnModeling/bpmn/panel/ServiceTaskPanel.vue`,
  `src/components/api/UEngineBackend.ts` (`listSqlTables` / `listSqlColumns`)
* 백엔드 — `process-service/.../org/uengine/five/sql/SqlMetadataController.java`

### 지원하지 않는 것

* uEngine 3 의 `ConnectionFactory: default`(엔진 자체 커넥션)와 JNDI 이름 지정은 되살리지 않았다.
  DataSource 빈 이름 또는 JDBC 직접 접속 두 가지만 쓴다.
* `QueryMode` 의 `Insert if not exist and Update if exist`(upsert)는 지원하지 않는다.
  uEngine 3 에서도 SQL 생성이 비어 있어 실제로 동작하지 않던 모드다.

## 샘플 / 테스트

* 배포용 샘플 정의: [definition-samples/sqlTaskProcess.bpmn](../definition-samples/sqlTaskProcess.bpmn)
* 모델링 타임 테스트: `process-service/src/test/java/org/uengine/test/SQLTaskModelingTest.java`
  (BPMN 파싱 → 속성 검증 → typed JSON round trip → 파싱한 정의를 실제로 실행)
  * 사용 BPMN: `process-service/src/test/resources/bpmn/sqlTask.bpmn`
* 실행 타임 테스트: `uengine-core/src/test/java/org/uengine/kernel/test/SQLTaskTest.java`
  (H2 인메모리 DB 대상으로 INSERT/SELECT/UPDATE/DELETE, 다중값 파라미터, 다중행 조회 검증)

```
mvn -pl uengine-core -am test -Dtest=SQLTaskTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl process-service -am test -Dtest=SQLTaskModelingTest -Dsurefire.failIfNoSpecifiedTests=false
```
