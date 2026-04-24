# Docker Compose (로컬 전용 이미지)

`process-service:v0.0.1`, `definition-service:v0.0.1`, `frontend:0.0.7`, `keycloak-gateway:0.0.5`는 **Docker Hub에 없는 로컬 태그**입니다.  
`docker-compose.yml`에는 `pull_policy: never`가 있어 **원격에서 pull 하지 않습니다.** 먼저 로컬 데몬에 이미지를 넣은 뒤 `docker compose up` 하세요.

## 한 번에 빌드해서 로컬에 태그 넣기 (권장)

Git Bash / Linux / macOS:

```bash
./infra/scripts/load-local-images.sh
```

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/load-local-images.ps1
```

이 스크립트는:

1. `mvn package`로 JAR 생성
2. 각 모듈 `Dockerfile`로 `docker build`하여 위 태그를 **로컬에 생성**

## 프론트 이미지(`frontend:0.0.7`)만 따로 주입

다른 PC에서 만든 tar가 있으면:

```bash
docker load -i path/to/something.tar
docker tag <방금 생긴 이미지> frontend:0.0.7
```

또는 `infra/local-images/frontend-0.0.7.tar` 로 복사해 두면 `load-local-images` 스크립트가 `docker load`를 시도합니다. (`infra/local-images/README.txt` 참고)

## Compose 기동

```bash
cd infra
docker compose up -d
```

Kafka / ZooKeeper / Keycloak은 공개 이미지이므로 평소처럼 `pull` 됩니다.

**Kafka 이미지:** `confluentinc/cp-kafka:latest`는 최신 빌드가 **KRaft 전용** 엔트리를 쓰는 경우가 있어, ZooKeeper 기반 예제와 맞지 않고 `KAFKA_PROCESS_ROLES is not set` 오류가 날 수 있습니다. 그래서 compose에서는 **`cp-kafka:7.6.1` / `cp-zookeeper:7.6.1`** 로 고정합니다.

## 인프라만 (ZooKeeper + Kafka + Keycloak)

앱/게이트웨이/프론트 없이 메시징·인증만 띄울 때:

```bash
cd infra
docker compose -f docker-compose.infra.yml up -d
```

이전에 다른 compose로 만든 컨테이너가 남아 `orphan` 경고가 나오면, 필요 시:

```bash
docker compose -f docker-compose.yml down --remove-orphans
```

또는 프로젝트 이름을 분리하려면 `-p infra-only` 같이 `-p`로 지정할 수 있습니다.
