#!/usr/bin/env bash
# 로컬 Docker 데몬에 애플리케이션 이미지를 넣습니다 (build 또는 tar load).
# 사용 전: 저장소 루트에서 실행하거나, 이 스크립트를 그대로 실행해도 됩니다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> [1/2] Maven JAR 빌드 (process-service, definition-service, keycloak-gateway)"
mvn -pl process-service,definition-service,keycloak-gateway -am package -DskipTests

echo "==> [2/2] docker build로 로컬 태그 생성"
docker build -t process-service:v0.0.1 -f process-service/Dockerfile process-service
docker build -t definition-service:v0.0.1 -f definition-service/Dockerfile definition-service
docker build -t keycloak-gateway:0.0.5 -f keycloak-gateway/Dockerfile keycloak-gateway

# 프론트는 Dockerfile이 없으면, 미리 만든 tar만 로드 (선택)
FRONTEND_TAR="$ROOT/infra/local-images/frontend-0.0.7.tar"
if [[ -f "$FRONTEND_TAR" ]]; then
  echo "==> optional: docker load $FRONTEND_TAR -> frontend:0.0.7"
  docker load -i "$FRONTEND_TAR"
else
  echo "==> optional: frontend:0.0.7 이미지가 없습니다."
  echo "    - 다른 PC에서 빌드한 tar를 두면: infra/local-images/frontend-0.0.7.tar"
  echo "    - 또는 로컬에서 직접: docker tag <이미지ID> frontend:0.0.7"
fi

echo "==> 완료. 로컬 이미지:"
docker images --format 'table {{.Repository}}:{{.Tag}}\t{{.ID}}' | head -n 1
docker images | grep -E 'process-service:v0.0.1|definition-service:v0.0.1|keycloak-gateway:0.0.5|frontend:0.0.7' || true
echo "다음: cd infra && docker compose up -d"
